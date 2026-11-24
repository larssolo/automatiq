import Foundation

/// Where the user stands: free trial, trial over, or bought. Same rules as the Android app:
/// 30 days, counted in calendar days (30 on the first day, 0 on the last), clock rollback can't extend it.
public enum LicenseStatus: Equatable, Sendable {
    case trial(daysLeft: Int)
    case expired
    case unlocked
}

public let trialDays = 30
/// The "ends soon" banner and reminder show from this many days left.
public let trialWarnDays = 5

public func licenseStatus(paywall: Bool, unlocked: Bool, trialStart: Date, lastSeen: Date?,
                          now: Date, timeZone: TimeZone = .current) -> LicenseStatus {
    if !paywall || unlocked { return .unlocked }
    let effectiveNow = max(now, lastSeen ?? now)
    let end = trialStart.addingTimeInterval(TimeInterval(trialDays) * 86_400)
    if end <= effectiveNow { return .expired }
    let left = CivilDate(end, in: timeZone).epochDay - CivilDate(effectiveNow, in: timeZone).epochDay
    return .trial(daysLeft: min(max(left, 0), trialDays))
}

/// The early-bird code. Not a secret (anyone can read it in the source); the store's own
/// once-per-account limit on the discount is what stops reuse.
public enum EarlyBird {
    public static let code = "TEXTFOX50"
    public static func accepts(_ input: String) -> Bool {
        input.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(code) == .orderedSame
    }
}
