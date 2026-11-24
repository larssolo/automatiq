import Foundation

/// A calendar date without time or zone, counted as days since 1970-01-01 ("epoch day"), the same
/// numbers Android's `LocalDate.toEpochDay()` uses, so exported macros keep their anchor and expiry.
public struct CivilDate: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let epochDay: Int

    public init(epochDay: Int) { self.epochDay = epochDay }

    /// Howard Hinnant's days-from-civil algorithm (proleptic Gregorian).
    public init(year: Int, month: Int, day: Int) {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        epochDay = era * 146_097 + doe - 719_468
    }

    /// The date [date] falls on in [timeZone].
    public init(_ date: Date, in timeZone: TimeZone) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let c = cal.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!)
    }

    public var components: (year: Int, month: Int, day: Int) {
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        return (yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }

    /// ISO weekday: 1 = Monday … 7 = Sunday. 1970-01-01 was a Thursday.
    public var isoWeekday: Int { ((epochDay + 3) % 7 + 7) % 7 + 1 }

    public func adding(days: Int) -> CivilDate { CivilDate(epochDay: epochDay + days) }

    /// The Monday of this date's week.
    public var monday: CivilDate { adding(days: 1 - isoWeekday) }

    /// The moment [hour]:[minute] (plus [extraMinutes]) happens on this date in [timeZone].
    public func at(hour: Int, minute: Int, extraMinutes: Int = 0, in timeZone: TimeZone) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let (y, m, d) = components
        let base = cal.date(from: DateComponents(year: y, month: m, day: d, hour: hour, minute: minute))!
        return base.addingTimeInterval(TimeInterval(extraMinutes * 60))
    }

    public static func < (a: CivilDate, b: CivilDate) -> Bool { a.epochDay < b.epochDay }

    public var description: String {
        let (y, m, d) = components
        return String(format: "%04d-%02d-%02d", y, m, d)
    }
}
