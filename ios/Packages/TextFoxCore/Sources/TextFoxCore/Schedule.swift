import Foundation

/// "HH:mm", parsed leniently: nil instead of a crash on a hand-edited import file.
public struct HhMm: Hashable, Sendable {
    public let hour: Int
    public let minute: Int

    public init?(_ s: String) {
        let parts = s.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count == 2, parts[0].count == 2, parts[1].count == 2,
              let h = Int(parts[0]), let m = Int(parts[1]), (0..<24).contains(h), (0..<60).contains(m)
        else { return nil }
        hour = h; minute = m
    }
}

public let allDays: Set<Int> = [1, 2, 3, 4, 5, 6, 7]

/// "Send on [min]–[max] random days of the allowed ones each week", seeded per macro.
public struct RandomDays: Hashable, Sendable {
    public let seed: String, min: Int, max: Int
    public init(seed: String, min: Int, max: Int) { self.seed = seed; self.min = min; self.max = max }
}

/// The repeating part of a scheduled macro. Same rules as the Android app's TimeUtils.
public struct Recurrence: Hashable, Sendable {
    public var days: Set<Int> = allDays
    public var weekInterval: Int = 1
    public var anchor: CivilDate?
    public var validUntil: CivilDate?
    public var randomDays: RandomDays?

    public init(days: Set<Int> = allDays, weekInterval: Int = 1, anchor: CivilDate? = nil,
                validUntil: CivilDate? = nil, randomDays: RandomDays? = nil) {
        self.days = days; self.weekInterval = weekInterval; self.anchor = anchor
        self.validUntil = validUntil; self.randomDays = randomDays
    }

    /// Weekday, start date, end date and week-interval checks: everything except the random pick.
    func isEligible(_ date: CivilDate) -> Bool {
        if let end = validUntil, date > end { return false }
        let allowed = days.isEmpty ? allDays : days
        guard allowed.contains(date.isoWeekday) else { return false }
        if let a = anchor, date < a { return false }
        let interval = max(weekInterval, 1)
        if interval > 1, let a = anchor {
            let weeks = (date.monday.epochDay - a.monday.epochDay) / 7
            if ((weeks % interval) + interval) % interval != 0 { return false }
        }
        return true
    }

    public func isScheduledDay(_ date: CivilDate) -> Bool {
        guard isEligible(date) else { return false }
        guard let r = randomDays, r.max > 0 else { return true }
        return randomDaysOfWeek(date.monday, r).contains(date)
    }

    /// Between min and max of the week's eligible days, picked at random but the same every time
    /// for one macro and one week, so the app, widgets and notifications always agree.
    public func randomDaysOfWeek(_ monday: CivilDate, _ r: RandomDays) -> Set<CivilDate> {
        let candidates = (0..<7).map { monday.adding(days: $0) }.filter(isEligible)
        if candidates.isEmpty { return [] }
        var rng = SeededGenerator(seed: daySeed(r.seed, monday.epochDay))
        let lo = Swift.min(Swift.max(r.min, 1), candidates.count)
        let hi = Swift.min(Swift.max(r.max, lo), candidates.count)
        let count = lo + Int(rng.next() % UInt64(hi - lo + 1))
        return Set(candidates.shuffled(using: &rng).prefix(count))
    }
}

/// Per-day offset in minutes, uniform in [-spread, +spread], the same every time for one macro and day.
public func jitterMinutes(seed: String, date: CivilDate, spread: Int) -> Int {
    guard spread > 0 else { return 0 }
    var rng = SeededGenerator(seed: daySeed(seed, date.epochDay))
    return Int(rng.next() % UInt64(2 * spread + 1)) - spread
}

/// The next time [time] occurs strictly after [now] on a scheduled day, or nil when the
/// recurrence has no more days (end date passed). The jitter is applied before comparing with now.
public func nextFireTime(_ time: HhMm, recurrence: Recurrence, now: Date, timeZone: TimeZone,
                         jitterSeed: String = "", jitterSpread: Int = 0) -> Date? {
    let today = CivilDate(now, in: timeZone)
    let start = recurrence.anchor.map { Swift.max($0, today) } ?? today
    let horizon = max(recurrence.weekInterval, 1) * 7 + 7
    for i in 0...horizon {
        let d = start.adding(days: i)
        guard recurrence.isScheduledDay(d) else { continue }
        let offset = jitterMinutes(seed: jitterSeed, date: d, spread: jitterSpread)
        let candidate = d.at(hour: time.hour, minute: time.minute, extraMinutes: offset, in: timeZone)
        if candidate > now { return candidate }
    }
    return nil
}

public extension Macro {
    var recurrence: Recurrence {
        Recurrence(days: Set(daysOfWeek), weekInterval: weekInterval,
                   anchor: anchorEpochDay.map(CivilDate.init(epochDay:)),
                   validUntil: validUntilEpochDay.map(CivilDate.init(epochDay:)),
                   randomDays: randomDays)
    }

    /// When this scheduled macro runs next; nil when it isn't scheduled, its time is broken or it has ended.
    func nextFire(after now: Date = Date(), timeZone: TimeZone = .current) -> Date? {
        guard triggerType == .scheduled, let t = scheduledTime.flatMap(HhMm.init) else { return nil }
        return nextFireTime(t, recurrence: recurrence, now: now, timeZone: timeZone,
                            jitterSeed: id, jitterSpread: randomSpreadMinutes)
    }
}

private let dayAbbr = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

/// "Every day", "Weekdays", "Weekend", or e.g. "Mon · Wed · Fri".
public func formatDays(_ days: Set<Int>) -> String {
    let d = days.isEmpty ? allDays : days
    if d.count == 7 { return "Every day" }
    if d == [1, 2, 3, 4, 5] { return "Weekdays" }
    if d == [6, 7] { return "Weekend" }
    return d.sorted().map { dayAbbr[$0 - 1] }.joined(separator: " · ")
}

/// e.g. "Every 2 weeks · 1–2 random of Weekdays".
public func formatRecurrence(_ days: Set<Int>, weekInterval: Int, randomMin: Int = 0, randomMax: Int = 0) -> String {
    let dayPart: String
    if randomMax <= 0 { dayPart = formatDays(days) }
    else if randomMin == randomMax { dayPart = "\(randomMax) random of \(formatDays(days))" }
    else { dayPart = "\(randomMin)–\(randomMax) random of \(formatDays(days))" }
    return weekInterval <= 1 ? dayPart : "Every \(weekInterval) weeks · \(dayPart)"
}
