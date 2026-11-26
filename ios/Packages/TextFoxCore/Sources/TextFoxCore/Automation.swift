import Foundation

// Sending through a Shortcuts automation. iPhone apps can't send texts themselves, so the user makes
// one "Time of Day" automation per clock time that runs the "TextFox Send" shortcut; TextFox decides
// which macros are due when it runs (random days, every other week and end dates included).

/// How far from a macro's send time an automation run still counts as that send.
public let automationWindow: TimeInterval = 30 * 60

public extension Macro {
    /// Sent as a text (the only kind Shortcuts' Send Message can take over).
    var sendsText: Bool { enabled && triggerType == .scheduled && actionType == .sms && !recipients.isEmpty }
}

/// The macros' sends that an automation running at [now] should send: one per macro, the planned
/// send closest to now within the window.
public func dueForAutomation(_ macros: [Macro], now: Date, timeZone: TimeZone = .current) -> [PlannedFire] {
    macros.filter(\.sendsText).compactMap { m in
        let near = m.dueFires(since: now.addingTimeInterval(-automationWindow - 1), until: now.addingTimeInterval(automationWindow), timeZone: timeZone)
        guard let at = near.min(by: { abs($0.timeIntervalSince(now)) < abs($1.timeIntervalSince(now)) }) else { return nil }
        return PlannedFire(macroId: m.id, at: at)
    }
}

/// The clock times ("08:00") that need an automation, earliest first.
public func automationTimes(_ macros: [Macro]) -> [String] {
    let times = macros.filter(\.sendsText).compactMap { m in m.scheduledTime.flatMap { HhMm($0) == nil ? nil : $0 } }
    return Array(Set(times)).sorted()
}

/// The automation time an automation running at [now] stands for, if it's within the window of one.
public func automationTime(at now: Date, among times: [String], timeZone: TimeZone = .current) -> String? {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = timeZone
    let c = cal.dateComponents([.hour, .minute], from: now)
    let minute = (c.hour ?? 0) * 60 + (c.minute ?? 0)
    func distance(_ t: String) -> Int {
        guard let h = HhMm(t) else { return .max }
        let d = abs(h.hour * 60 + h.minute - minute)
        return min(d, 24 * 60 - d)
    }
    guard let best = times.min(by: { distance($0) < distance($1) }), distance(best) <= Int(automationWindow / 60) else { return nil }
    return best
}
