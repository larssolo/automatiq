import Foundation

/// One planned send: which macro, and when.
public struct PlannedFire: Hashable, Sendable {
    public let macroId: String
    public let at: Date
    public init(macroId: String, at: Date) { self.macroId = macroId; self.at = at }

    /// The notification id: stable for the same macro and time, so re-planning doesn't duplicate.
    public var id: String { "fire:\(macroId):\(Int64(at.timeIntervalSince1970))" }

    public init?(id: String) {
        let parts = id.split(separator: ":")
        guard parts.count == 3, parts[0] == "fire", let t = Int64(parts[2]) else { return nil }
        macroId = String(parts[1]); at = Date(timeIntervalSince1970: TimeInterval(t))
    }
}

public extension Macro {
    /// The next [count] times this scheduled macro runs after [now]; empty when it's off or ended.
    func upcomingFires(after now: Date, count: Int, timeZone: TimeZone = .current) -> [Date] {
        guard enabled, triggerType == .scheduled else { return [] }
        var out: [Date] = []
        var t = now
        while out.count < count, let next = nextFire(after: t, timeZone: timeZone) {
            out.append(next)
            // A one-off fires once.
            if !repeatDaily { break }
            t = next
        }
        return out
    }
}

/// iOS keeps at most 64 pending notifications per app; TextFox plans the soonest ones and refills
/// whenever the app opens or refreshes in the background.
public let notificationBudget = 60

/// The soonest planned sends across all macros, within the budget. Every macro gets its next send
/// first, so one busy macro can't crowd the others out.
public func planFires(_ macros: [Macro], after now: Date, budget: Int = notificationBudget,
                      timeZone: TimeZone = .current) -> [PlannedFire] {
    let perMacro = macros.map { m in m.upcomingFires(after: now, count: budget, timeZone: timeZone).map { PlannedFire(macroId: m.id, at: $0) } }
    var chosen = perMacro.compactMap(\.first)
    let rest = perMacro.flatMap { $0.dropFirst() }.sorted { $0.at < $1.at }
    chosen.sort { $0.at < $1.at }
    if chosen.count >= budget { return Array(chosen.prefix(budget)) }
    return (chosen + rest.prefix(budget - chosen.count)).sorted { $0.at < $1.at }
}

public extension Macro {
    /// The sends that came due after [since] and up to [now], newest last, at most [limit] of them.
    /// The app logs these when it opens, since iOS shows the notifications without waking the app.
    func dueFires(since: Date, until now: Date, limit: Int = 5, timeZone: TimeZone = .current) -> [Date] {
        guard enabled, triggerType == .scheduled, since < now else { return [] }
        var out: [Date] = []
        var t = since
        while let next = nextFire(after: t, timeZone: timeZone), next <= now {
            out.append(next)
            if !repeatDaily { break }
            t = next
        }
        return Array(out.suffix(limit))
    }
}
