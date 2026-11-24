import Foundation

// What the list and editor show about a macro, the same wording as the Android app.

/// Material 500 colours (ARGB), bold enough to read on the dark background. Same order as Android.
public let cardColorPalette: [Int64] = [
    0xFFF4_4336, 0xFFE9_1E63, 0xFF9C_27B0, 0xFF3F_51B5, 0xFF00_BCD4,
    0xFF4C_AF50, 0xFFCD_DC39, 0xFFFF_9800, 0xFFFF_5722, 0xFF00_9688,
]

public extension Macro {
    /// The explicit colour, or one derived from the id (stable across launches, as on Android).
    var accentColor: Int64 {
        if cardColor != 0 { return cardColor }
        let n = Int(stableHash(id)), size = cardColorPalette.count
        return cardColorPalette[((n % size) + size) % size]
    }

    /// e.g. "08:00 · Weekdays", "Auto-reply · ****5678", "Manual trigger", plus the action when it isn't SMS.
    func compactSummary(timeZone: TimeZone = .current) -> String {
        let trigger: String
        switch triggerType {
        case .scheduled:
            var s = scheduledTime ?? "--:--"
            if !repeatDaily {
                let day = anchorEpochDay ?? validUntilEpochDay
                s += " · " + (day.map { "\(shortDate(CivilDate(epochDay: $0), timeZone: timeZone)) (once)" } ?? "once")
            } else {
                let days = formatRecurrence(Set(daysOfWeek), weekInterval: weekInterval,
                                            randomMin: randomDaysMin, randomMax: randomDaysMax)
                if !days.isEmpty { s += " · " + days }
            }
            trigger = s
        case .incoming:
            trigger = "Auto-reply" + (matchSender.flatMap { $0.isEmpty ? nil : " · " + maskPhone($0) } ?? "")
        case .location:
            trigger = (geofenceTransition == 2 ? "On departure" : "On arrival")
                + (radiusMeters.map { " · \(Int($0)) m" } ?? "")
        case .manual: trigger = "Manual trigger"
        case .missedCall:
            trigger = "Missed call" + (matchSender.flatMap { $0.isEmpty ? nil : " · " + maskPhone($0) } ?? "")
        case .charging: trigger = triggerOnConnect ? "When plugged in" : "When unplugged"
        case .bluetooth:
            trigger = (triggerOnConnect ? "BT connect" : "BT disconnect")
                + (triggerTargetLabel.flatMap { $0.isEmpty ? nil : " · " + $0 } ?? "")
        case .wifi:
            trigger = (triggerOnConnect ? "Wi-Fi connect" : "Wi-Fi disconnect")
                + (triggerTargetLabel.flatMap { $0.isEmpty ? nil : " · " + $0 } ?? "")
        }
        let action: String? = switch actionType {
        case .sms: nil
        case .sound: "Sound"
        case .reminder: "Reminder"
        case .webhook: "Webhook"
        }
        return action.map { "\(trigger) · \($0)" } ?? trigger
    }
}

/// "****5678": enough to recognise a number in a list without showing all of it.
public func maskPhone(_ s: String) -> String {
    let d = s.filter(\.isNumber)
    return d.count <= 4 ? s : "****" + d.suffix(4)
}

private func formatter(_ pattern: String, _ tz: TimeZone) -> DateFormatter {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_GB")
    f.timeZone = tz
    f.dateFormat = pattern
    return f
}

/// "Wed 21 Oct"; the year is added only when it isn't this year.
public func shortDate(_ d: CivilDate, now: Date = Date(), timeZone: TimeZone = .current) -> String {
    let date = d.at(hour: 12, minute: 0, in: timeZone)
    let sameYear = d.components.year == CivilDate(now, in: timeZone).components.year
    return formatter(sameYear ? "EEE d MMM" : "EEE d MMM yyyy", timeZone).string(from: date)
}

/// "Mon 19 Oct 2026".
public func prettyDate(_ d: CivilDate, timeZone: TimeZone = .current) -> String {
    formatter("EEE d MMM yyyy", timeZone).string(from: d.at(hour: 12, minute: 0, in: timeZone))
}

/// "today 14:55", "tomorrow 08:15", "Wed 11:13" within a week, else "Mon 9 Nov 07:07".
public func formatNextFire(_ at: Date, now: Date = Date(), timeZone: TimeZone = .current) -> String {
    let today = CivilDate(now, in: timeZone), day = CivilDate(at, in: timeZone)
    let time = formatter("HH:mm", timeZone).string(from: at)
    switch day.epochDay - today.epochDay {
    case 0: return "today \(time)"
    case 1: return "tomorrow \(time)"
    case 2..<7: return "\(formatter("EEE", timeZone).string(from: at)) \(time)"
    default:
        let sameYear = day.components.year == today.components.year
        return "\(formatter(sameYear ? "EEE d MMM" : "EEE d MMM yyyy", timeZone).string(from: at)) \(time)"
    }
}

/// The editor's names for the message variables; the token itself is what gets inserted.
public func tokenLabel(_ token: String) -> String {
    switch token {
    case "{dato}": "Date"
    case "{tid}": "Time"
    case "{ugedag}": "Weekday"
    case "{navn}": "Macro name"
    case "{modtager}": "Recipient"
    case "{afsender}": "Sender"
    default: token
    }
}
