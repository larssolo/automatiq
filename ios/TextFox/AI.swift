import Foundation
import Security
import TextFoxCore

/// The Gemini key in the iPhone's Keychain (never in the store file or backups).
enum GeminiKey {
    private static let account = "gemini_api_key"
    private static let service = "app.kodekid.textfox"

    static var value: String {
        get {
            var out: CFTypeRef?
            let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                    kSecAttrAccount as String: account, kSecReturnData as String: true]
            guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return "" }
            return String(decoding: d, as: UTF8.self)
        }
        set {
            let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                       kSecAttrAccount as String: account]
            SecItemDelete(base as CFDictionary)
            let v = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !v.isEmpty else { return }
            var add = base
            add[kSecValueData as String] = Data(v.utf8)
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            SecItemAdd(add as CFDictionary, nil)
        }
    }
}

/// When and how TextFox asks Gemini. Every path falls back to the macro's own text.
@MainActor
enum AI {
    /// AI may run: there's a key and the user agreed to send message text to Google.
    static var ready: Bool { MacroStore.shared.settings.aiConsent && !GeminiKey.value.isEmpty }

    static var client: GeminiClient { GeminiClient(apiKey: GeminiKey.value, model: MacroStore.shared.settings.geminiModel) }

    /// The text to send for [macro] at [at]: an AI variation when it's switched on, otherwise (or if
    /// Gemini fails) the macro's own text. The note says what happened, for the log.
    static func text(for macro: Macro, at: Date = Date()) async -> (text: String, note: String?) {
        let fixed = expandTemplate(macro.messageBody, now: at, macroName: macro.name, locale: Locale(identifier: "en_GB"))
        guard macro.aiReplyEnabled, macro.triggerType == .scheduled || macro.triggerType == .manual else { return (fixed, nil) }
        guard ready else { return (fixed, GeminiKey.value.isEmpty ? "AI off: no API key — fixed text" : "AI paused: agree to AI in Settings — fixed text") }
        do {
            let r = try await client.vary(fixed, instruction: macro.aiReplyInstruction)
            return (r.text, "AI · \(r.model)")
        } catch {
            return (fixed, "AI failed (\(error.localizedDescription)) — fixed text")
        }
    }

    /// The reply to an incoming text: written by Gemini for AI auto-replies, else the macro's text.
    static func reply(for macro: Macro, sender: String, incoming: String) async -> (text: String, note: String?) {
        let fixed = expandTemplate(macro.messageBody, now: Date(), macroName: macro.name, sender: sender, locale: Locale(identifier: "en_GB"))
        guard macro.aiReplyEnabled else { return (fixed, nil) }
        guard ready else { return (fixed, "AI off — fixed text") }
        do {
            let instruction = [macro.aiReplyInstruction, macro.messageBody].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ". ")
            let r = try await client.reply(to: incoming, instruction: instruction.isEmpty ? nil : instruction)
            return (r.text, "AI · \(r.model)")
        } catch {
            return (fixed, "AI failed (\(error.localizedDescription)) — fixed text")
        }
    }
}
