import Foundation
import Testing
@testable import TextFoxCore

let cph = TimeZone(identifier: "Europe/Copenhagen")!

func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
    CivilDate(year: y, month: m, day: d).at(hour: h, minute: min, in: cph)
}

@Suite struct CivilDateTests {
    @Test func epochDayMatchesAndroid() {
        #expect(CivilDate(year: 1970, month: 1, day: 1).epochDay == 0)
        #expect(CivilDate(year: 2026, month: 11, day: 18).epochDay == 20_775)
        #expect(CivilDate(epochDay: 20_775).description == "2026-11-18")
    }

    @Test func isoWeekday() {
        #expect(CivilDate(year: 2026, month: 11, day: 23).isoWeekday == 1) // Monday
        #expect(CivilDate(year: 2026, month: 11, day: 29).isoWeekday == 7) // Sunday
        #expect(CivilDate(year: 2026, month: 11, day: 26).monday == CivilDate(year: 2026, month: 11, day: 23))
    }
}

@Suite struct ScheduleTests {
    let eight = HhMm("08:00")!

    @Test func parsesTimesStrictly() {
        #expect(HhMm("08:30") != nil)
        #expect(HhMm("8:30") == nil)
        #expect(HhMm("24:00") == nil)
        #expect(HhMm("ab:cd") == nil)
    }

    @Test func laterTodayOrTomorrow() {
        let r = Recurrence()
        #expect(nextFireTime(eight, recurrence: r, now: at(2026, 11, 24, 7), timeZone: cph) == at(2026, 11, 24, 8))
        #expect(nextFireTime(eight, recurrence: r, now: at(2026, 11, 24, 8), timeZone: cph) == at(2026, 11, 25, 8))
    }

    @Test func onlyChosenWeekdays() {
        // Tuesday 24 Nov, weekend only -> Saturday 28 Nov.
        let r = Recurrence(days: [6, 7])
        #expect(nextFireTime(eight, recurrence: r, now: at(2026, 11, 24, 9), timeZone: cph) == at(2026, 11, 28, 8))
    }

    @Test func everyOtherWeekFollowsTheAnchor() {
        // Anchored Monday 23 Nov, Mondays every 2 weeks: 23 Nov, 7 Dec, …
        let r = Recurrence(days: [1], weekInterval: 2, anchor: CivilDate(year: 2026, month: 11, day: 23))
        #expect(nextFireTime(eight, recurrence: r, now: at(2026, 11, 23, 9), timeZone: cph) == at(2026, 12, 7, 8))
    }

    @Test func futureStartDateWaits() {
        let r = Recurrence(anchor: CivilDate(year: 2026, month: 12, day: 10))
        #expect(nextFireTime(eight, recurrence: r, now: at(2026, 11, 24, 9), timeZone: cph) == at(2026, 12, 10, 8))
    }

    @Test func endDateStopsIt() {
        let r = Recurrence(validUntil: CivilDate(year: 2026, month: 11, day: 24))
        #expect(nextFireTime(eight, recurrence: r, now: at(2026, 11, 24, 9), timeZone: cph) == nil)
    }

    @Test func jitterStaysInRangeAndIsStable() {
        let d = CivilDate(year: 2026, month: 11, day: 24)
        for spread in [1, 5, 30] {
            let v = jitterMinutes(seed: "macro-1", date: d, spread: spread)
            #expect(abs(v) <= spread)
            #expect(v == jitterMinutes(seed: "macro-1", date: d, spread: spread))
        }
        #expect(jitterMinutes(seed: "x", date: d, spread: 0) == 0)
    }

    @Test func randomDaysPickWithinBoundsAndRepeat() {
        let r = Recurrence(days: [1, 2, 3, 4, 5], randomDays: RandomDays(seed: "m", min: 1, max: 2))
        let monday = CivilDate(year: 2026, month: 11, day: 23)
        let picked = r.randomDaysOfWeek(monday, r.randomDays!)
        #expect((1...2).contains(picked.count))
        #expect(picked.allSatisfy { $0.isoWeekday <= 5 })
        #expect(picked == r.randomDaysOfWeek(monday, r.randomDays!))
    }

    @Test func labels() {
        #expect(formatDays(allDays) == "Every day")
        #expect(formatDays([1, 2, 3, 4, 5]) == "Weekdays")
        #expect(formatDays([1, 3]) == "Mon · Wed")
        #expect(formatRecurrence([1, 2, 3, 4, 5], weekInterval: 2, randomMin: 1, randomMax: 2) == "Every 2 weeks · 1–2 random of Weekdays")
    }

    @Test func stableHashIsJavas() {
        #expect(stableHash("") == 0)
        #expect(stableHash("hello") == 99_162_322)
    }
}

@Suite struct MessageTests {
    @Test func expandsTokens() {
        let s = expandTemplate("{navn}: {dato} {tid} {ugedag} {modtager} {afsender} {ukendt}",
                               now: at(2026, 11, 24, 9, 5), timeZone: cph, macroName: "Mor",
                               sender: "+4512345678", recipientName: "Anna", locale: Locale(identifier: "da_DK"))
        #expect(s == "Mor: 24-11-2026 09:05 tirsdag Anna +4512345678 {ukendt}")
    }

    @Test func leavesReplyTokensWhenUnknown() {
        let s = expandTemplate("{afsender}", now: Date(), macroName: "x")
        #expect(s == "{afsender}")
    }

    @Test func numbersMatchOnTheirEnding() {
        #expect(senderMatches(filter: "+45 12 34 56 78", incoming: "12345678"))
        #expect(senderMatches(filter: "4512345678", incoming: "+4512345678"))
        #expect(!senderMatches(filter: "12345678", incoming: "87654321"))
        #expect(!senderMatches(filter: "", incoming: "123"))
    }

    @Test func incomingFiltersAreAnded() {
        var m = Macro(name: "r", triggerType: .incoming, recipients: [], messageBody: "ok")
        #expect(m.matchesIncoming(sender: "1", body: "anything"))
        m.matchKeyword = "pris"
        #expect(m.matchesIncoming(sender: "1", body: "Hvad er PRISEN?"))
        m.matchSender = "12345678"
        #expect(!m.matchesIncoming(sender: "99999999", body: "pris"))
    }

    @Test func quietHoursWrapMidnight() {
        #expect(isWithinQuietHours(23 * 60, start: 22 * 60, end: 7 * 60))
        #expect(isWithinQuietHours(6 * 60, start: 22 * 60, end: 7 * 60))
        #expect(!isWithinQuietHours(12 * 60, start: 22 * 60, end: 7 * 60))
        #expect(!isWithinQuietHours(0, start: 0, end: 0))
        #expect(minutesUntilQuietEnd(6 * 60, start: 22 * 60, end: 7 * 60) == 60)
    }
}

@Suite struct LicenseTests {
    let start = at(2026, 11, 18, 12, 26)
    func status(_ now: Date, lastSeen: Date? = nil, unlocked: Bool = false, paywall: Bool = true) -> LicenseStatus {
        licenseStatus(paywall: paywall, unlocked: unlocked, trialStart: start, lastSeen: lastSeen, now: now, timeZone: cph)
    }

    @Test func countsCalendarDays() {
        #expect(status(start) == .trial(daysLeft: 30))
        #expect(status(at(2026, 11, 19, 0, 1)) == .trial(daysLeft: 29))
        #expect(status(at(2026, 11, 20, 8, 19)) == .trial(daysLeft: 28))
        #expect(status(at(2026, 12, 18, 9)) == .trial(daysLeft: 0))
    }

    @Test func endsAfterThirtyDays() {
        #expect(status(start.addingTimeInterval(30 * 86_400)) == .expired)
    }

    @Test func clockRollbackDoesNotHelp() {
        #expect(status(start.addingTimeInterval(86_400), lastSeen: start.addingTimeInterval(31 * 86_400)) == .expired)
    }

    @Test func purchaseOrNoPaywallUnlocks() {
        #expect(status(start.addingTimeInterval(90 * 86_400), unlocked: true) == .unlocked)
        #expect(status(start.addingTimeInterval(90 * 86_400), paywall: false) == .unlocked)
    }

    @Test func earlyBirdCode() {
        #expect(EarlyBird.accepts(" textfox50 "))
        #expect(!EarlyBird.accepts("TEXTFOX5"))
    }
}

@Suite struct BackupTests {
    @Test func readsAnAndroidBackup() throws {
        let json = """
        {"version":1,"macros":[{"id":"a","name":"Good morning","triggerType":"SCHEDULED","scheduledTime":"08:00",
          "repeatDaily":true,"recipients":["+4512345678"],"messageBody":"Hi {modtager}","enabled":true,
          "lastTriggeredAt":null,"lastStatus":"SUCCESS","createdAt":1,"daysOfWeek":[1,2,3],"aiSendMode":"AUTO",
          "actionType":"SOMETHING_NEW","futureField":42}],
         "folders":[{"id":"f","name":"Family"}],"settings":{"quiet_start":"1320"}}
        """
        let b = try Backup.parse(Data(json.utf8))
        #expect(b.macros.count == 1)
        let m = b.macros[0]
        #expect(m.recipients == ["+4512345678"])
        #expect(m.daysOfWeek == [1, 2, 3])
        #expect(m.aiSendMode == .auto)
        #expect(m.actionType == .sms) // unknown action falls back
        #expect(b.folders.first?.expanded == true)
        #expect(b.settings["quiet_start"] == "1320")
    }

    @Test func readsLegacyMacroListWithSingleNumber() throws {
        let json = #"[{"id":"a","name":"x","triggerType":"MANUAL","scheduledTime":null,"recipientNumber":"123","messageBody":"hi"}]"#
        let b = try Backup.parse(Data(json.utf8))
        #expect(b.macros[0].recipients == ["123"])
    }

    @Test func rejectsABrokenTime() {
        let json = #"[{"id":"a","name":"x","triggerType":"SCHEDULED","scheduledTime":"8 am","messageBody":"hi"}]"#
        #expect(throws: (any Error).self) { try Backup.parse(Data(json.utf8)) }
    }

    @Test func roundTrips() throws {
        var m = Macro(name: "x", triggerType: .scheduled, scheduledTime: "07:15", recipients: ["1"], messageBody: "y")
        m.daysOfWeek = [5, 1]
        let data = try JSONEncoder().encode([m])
        let back = try Backup.parse(data).macros[0]
        #expect(back.scheduledTime == "07:15")
        #expect(back.daysOfWeek == [1, 5])
    }
}
