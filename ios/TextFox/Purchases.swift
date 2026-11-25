import Foundation
import Observation
import Security
import StoreKit
import TextFoxCore

/// The products in App Store Connect. Apple's rule for a trial before a one-time purchase: a free
/// non-consumable named like "30-Day Free Trial" that the user "buys" to start it.
enum ProductID {
    static let trial = "textfox_trial"
    static let unlock = "textfox_unlock"
    /// The same unlock at half price, shown after the early-bird code is entered.
    static let unlockEarlyBird = "textfox_unlock_earlybird"
    static let all = [trial, unlock, unlockEarlyBird]
}

/// Where the user stands, including "hasn't started the trial yet" (an iPhone-only state).
enum Access: Equatable {
    case notStarted
    case license(LicenseStatus)

    var canRun: Bool {
        switch self {
        case .notStarted: false
        case .license(.expired): false
        case .license: true
        }
    }
}

/// StoreKit 2: products, purchases, restore and the license that follows from them.
@MainActor @Observable
final class Purchases {
    static let shared = Purchases()

    private(set) var products: [String: Product] = [:]
    private(set) var unlocked = false
    private(set) var trialStart: Date?
    private(set) var busy = false
    var earlyBird = false
    var message: String?

    /// Debug builds can run without the paywall (`-paywall NO`), as the UI tests do.
    private let paywall: Bool = {
        #if DEBUG
        if UserDefaults.standard.object(forKey: "paywall") != nil { return UserDefaults.standard.bool(forKey: "paywall") }
        #endif
        return true
    }()

    private var updates: Task<Void, Never>?

    init() {
        trialStart = Keychain.date("trial_start")
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let t) = result { await t.finish() }
                await self?.refresh()
            }
        }
    }

    var access: Access {
        if !paywall { return .license(.unlocked) }
        if unlocked { return .license(.unlocked) }
        guard let start = trialStart else { return .notStarted }
        return .license(licenseStatus(paywall: true, unlocked: false, trialStart: start, lastSeen: lastSeen, now: Date()))
    }

    /// When the trial ends (nil when unlocked or not started); nothing is planned after it.
    var trialEnd: Date? {
        guard paywall, !unlocked, let start = trialStart else { return nil }
        return start.addingTimeInterval(TimeInterval(trialDays) * 86_400)
    }

    /// The latest time the app has seen, so turning the clock back can't stretch the trial.
    private var lastSeen: Date? { Keychain.date("last_seen") }

    func touch() {
        let now = Date()
        if now > (lastSeen ?? .distantPast) { Keychain.set(date: now, "last_seen") }
    }

    func load() async {
        touch()
        if products.isEmpty, let list = try? await Product.products(for: ProductID.all) {
            products = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
        }
        await refresh()
    }

    /// Reads what the user owns from StoreKit.
    func refresh() async {
        var owns = false
        for await result in Transaction.currentEntitlements {
            guard case .verified(let t) = result, t.revocationDate == nil else { continue }
            switch t.productID {
            case ProductID.unlock, ProductID.unlockEarlyBird: owns = true
            case ProductID.trial:
                // The trial's purchase date is the real start, also after a reinstall.
                if trialStart == nil || t.purchaseDate < trialStart! { trialStart = t.purchaseDate; Keychain.set(date: t.purchaseDate, "trial_start") }
            default: break
            }
        }
        unlocked = owns
    }

    var unlockProduct: Product? { products[earlyBird ? ProductID.unlockEarlyBird : ProductID.unlock] ?? products[ProductID.unlock] }

    /// "Unlock for $6.99" with the store's own price, or a plain label before it loads.
    var unlockLabel: String { unlockProduct.map { "Unlock for \($0.displayPrice)" } ?? "Unlock TextFox" }

    func startTrial() async { await buy(ProductID.trial) }
    func unlock() async { await buy(earlyBird ? ProductID.unlockEarlyBird : ProductID.unlock) }

    private func buy(_ id: String) async {
        guard let product = products[id] ?? (id == ProductID.unlockEarlyBird ? products[ProductID.unlock] : nil) else {
            message = "The App Store isn't reachable right now. Try again in a moment."
            return
        }
        busy = true
        defer { busy = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                if case .verified(let t) = result { await t.finish() }
                await refresh()
                message = nil
            case .pending: message = "Waiting for approval — TextFox unlocks as soon as Apple confirms the payment."
            case .userCancelled: break
            @unknown default: break
            }
        } catch {
            message = error.localizedDescription
        }
    }

    func restore() async {
        busy = true
        defer { busy = false }
        try? await AppStore.sync()
        await refresh()
        message = unlocked ? "TextFox is unlocked." : "No purchase found for this Apple ID."
    }

    func apply(code: String) -> Bool {
        guard EarlyBird.accepts(code) else { return false }
        earlyBird = true
        return true
    }
}

/// Small Keychain store for the trial dates: they survive a reinstall, unlike app files.
enum Keychain {
    private static let service = "app.kodekid.textfox.license"

    static func date(_ key: String) -> Date? {
        var out: CFTypeRef?
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: key, kSecReturnData as String: true]
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data,
              let t = Double(String(decoding: d, as: UTF8.self)) else { return nil }
        return Date(timeIntervalSince1970: t)
    }

    static func set(date: Date, _ key: String) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                   kSecAttrAccount as String: key]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = Data(String(date.timeIntervalSince1970).utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}
