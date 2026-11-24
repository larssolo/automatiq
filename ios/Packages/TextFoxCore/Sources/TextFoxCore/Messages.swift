import Foundation

/// Tokens the user can put in a message; shown as hints in the editor.
public let templateTokens = ["{dato}", "{tid}", "{ugedag}", "{navn}", "{modtager}"]
/// Reply macros only: the other party's number.
public let senderToken = "{afsender}"
public let recipientToken = "{modtager}"

/// Fills in {dato} (dd-MM-yyyy), {tid} (HH:mm), {ugedag} (weekday in [locale]), {navn} (macro name),
/// {modtager} and {afsender} when known. Unknown tokens stay as they are.
public func expandTemplate(_ body: String, now: Date, timeZone: TimeZone = .current, macroName: String,
                           sender: String? = nil, recipientName: String? = nil,
                           locale: Locale = .current) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = timeZone
    let c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: now)
    let weekdayFormat = DateFormatter()
    weekdayFormat.locale = locale
    weekdayFormat.timeZone = timeZone
    weekdayFormat.dateFormat = "EEEE"
    var s = body
        .replacingOccurrences(of: "{dato}", with: String(format: "%02d-%02d-%04d", c.day!, c.month!, c.year!))
        .replacingOccurrences(of: "{tid}", with: String(format: "%02d:%02d", c.hour!, c.minute!))
        .replacingOccurrences(of: "{ugedag}", with: weekdayFormat.string(from: now))
        .replacingOccurrences(of: "{navn}", with: macroName)
    if let sender { s = s.replacingOccurrences(of: senderToken, with: sender) }
    if let recipientName { s = s.replacingOccurrences(of: recipientToken, with: recipientName) }
    return s
}

private func digits(_ s: String) -> String { s.filter(\.isNumber) }

/// Two numbers match when their last digits line up, so "+45 12 34 56 78", "4512345678" and
/// "12345678" are the same contact regardless of country code and spacing.
public func senderMatches(filter: String, incoming: String) -> Bool {
    let a = digits(filter), b = digits(incoming)
    if a.isEmpty || b.isEmpty { return false }
    let len = min(min(a.count, b.count), 8)
    if len < 6 { return a == b }
    return a.suffix(len) == b.suffix(len)
}

public extension Macro {
    /// Auto-reply: blank sender or keyword filters match anything; both filters must pass.
    func matchesIncoming(sender: String, body: String) -> Bool {
        let senderOk = matchSender.map { $0.trimmingCharacters(in: .whitespaces).isEmpty || senderMatches(filter: $0, incoming: sender) } ?? true
        let keyword = matchKeyword?.trimmingCharacters(in: .whitespaces) ?? ""
        return senderOk && (keyword.isEmpty || body.localizedCaseInsensitiveContains(keyword))
    }
}

/// Whether [minuteOfDay] is inside the quiet window [start, end). The window may wrap past midnight;
/// start == end is an empty window, so a mis-set 00:00–00:00 can't silence everything.
public func isWithinQuietHours(_ minuteOfDay: Int, start: Int, end: Int) -> Bool {
    if start == end { return false }
    return start < end ? (start..<end).contains(minuteOfDay) : (minuteOfDay >= start || minuteOfDay < end)
}

/// Minutes until the quiet window ends; 0 outside it.
public func minutesUntilQuietEnd(_ minuteOfDay: Int, start: Int, end: Int) -> Int {
    guard isWithinQuietHours(minuteOfDay, start: start, end: end) else { return 0 }
    return ((end - minuteOfDay) + 1440) % 1440
}

/// The system prompt for an AI variation of a scheduled message. Same wording as on Android.
public func varyPrompt(instruction: String?) -> String {
    var s = "You rewrite SMS messages. The user sends you a message they send out regularly. "
    s += "Write ONE fresh variation of it: same meaning, same language, roughly the same "
    s += "length, natural and human — never a copy of the original. "
    s += "Keep exactly the original's language — never switch to a related language. "
    s += "Reply ONLY with the message itself — no preamble, no explanation, no quotes. "
    if let i = instruction, !i.trimmingCharacters(in: .whitespaces).isEmpty { s += "Style guidance: \(i)" }
    return s
}
