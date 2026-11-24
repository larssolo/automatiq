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

@Suite struct PresentationTests {
    @Test func accentIsStableAndFromPalette() {
        let m = Macro(id: "abc", name: "x", triggerType: .manual, recipients: [], messageBody: "")
        // Java: "abc".hashCode() = 96354, 96354 mod 10 = 4 → cyan.
        #expect(m.accentColor == cardColorPalette[4])
        var n = m; n.cardColor = 0xFFFF_0000
        #expect(n.accentColor == 0xFFFF_0000)
        let neg = Macro(id: "polygenelubricants", name: "x", triggerType: .manual, recipients: [], messageBody: "")
        #expect(cardColorPalette.contains(neg.accentColor)) // negative hash still maps into the palette
    }

    @Test func summaries() {
        var m = Macro(name: "x", triggerType: .scheduled, scheduledTime: "08:00", recipients: [], messageBody: "")
        m.daysOfWeek = [1, 2, 3, 4, 5]
        #expect(m.compactSummary(timeZone: cph) == "08:00 · Weekdays")
        m.triggerType = .incoming; m.matchSender = "+45 12 34 56 78"
        #expect(m.compactSummary(timeZone: cph) == "Auto-reply · ****5678")
        m.triggerType = .manual; m.actionType = .reminder
        #expect(m.compactSummary(timeZone: cph) == "Manual trigger · Reminder")
    }

    @Test func nextFireWording() {
        let now = at(2026, 11, 24, 10)
        #expect(formatNextFire(at(2026, 11, 24, 14, 55), now: now, timeZone: cph) == "today 14:55")
        #expect(formatNextFire(at(2026, 11, 25, 8, 15), now: now, timeZone: cph) == "tomorrow 08:15")
        #expect(formatNextFire(at(2026, 11, 27, 11, 13), now: now, timeZone: cph) == "Fri 11:13")
        #expect(formatNextFire(at(2026, 12, 7, 7, 7), now: now, timeZone: cph) == "Mon 7 Dec 07:07")
        #expect(formatNextFire(at(2027, 1, 4, 7, 7), now: now, timeZone: cph) == "Mon 4 Jan 2027 07:07")
    }
}

@Suite struct FolderLayoutTests {
    func macro(_ id: String, folder: String? = nil, order: Int) -> Macro {
        var m = Macro(id: id, name: id, triggerType: .manual, recipients: [], messageBody: "")
        m.folderId = folder; m.sortOrder = order
        return m
    }

    @Test func flattensFoldersWithMembers() {
        var fam = Folder(id: "fam", name: "Family"); fam.sortOrder = 0
        var work = Folder(id: "work", name: "Work"); work.sortOrder = 2; work.expanded = false
        let macros = [macro("a", folder: "fam", order: 1), macro("b", folder: "fam", order: 0),
                      macro("c", order: 1), macro("d", folder: "work", order: 0), macro("e", folder: "gone", order: 3)]
        let ids = flattenRows(folders: [fam, work], macros: macros).map(\.id)
        #expect(ids == ["f:fam", "m:b", "m:a", "m:c", "f:work", "m:e"])
    }

    @Test func dropTakesTheFolderAbove() {
        let fam = Folder(id: "fam", name: "Family")
        let rows: [ListRow] = [.folder(fam, memberCount: 1, activeCount: 1), .macro(macro("a", folder: "fam", order: 0)),
                               .macro(macro("x", order: 0)), .macro(macro("c", order: 1))]
        #expect(folderForDrop(rows: rows, at: 2) == "fam")
        #expect(folderForDrop(rows: rows, at: 1) == "fam")
        #expect(folderForDrop(rows: rows, at: 3) == nil)
        #expect(folderForDrop(rows: rows, at: 0) == nil)
    }

    @Test func ordersPerSpace() {
        let fam = Folder(id: "fam", name: "Family")
        let o = layoutOrders([.macro(macro("c", order: 9)), .folder(fam, memberCount: 2, activeCount: 0),
                              .macro(macro("a", folder: "fam", order: 5)), .macro(macro("b", folder: "fam", order: 1))])
        #expect(o.rootMacroOrder == ["c": 0])
        #expect(o.folderOrder == ["fam": 1])
        #expect(o.memberOrder == ["fam": ["a": 0, "b": 1]])
    }
}

@Suite struct EditorDraftTests {
    let today = CivilDate(year: 2026, month: 11, day: 24) // Tuesday

    @Test func roundTripsAMacro() {
        var m = Macro(id: "x", name: "Mom", triggerType: .scheduled, scheduledTime: "08:00",
                      recipients: ["+4512345678"], messageBody: "Hi")
        m.daysOfWeek = [1, 3, 5]; m.randomSpreadMinutes = 10; m.cardColor = cardColorPalette[2]
        let back = EditorDraft(m).toMacro(id: "x", today: today)
        #expect(back == m)
    }

    @Test func oneOffAnchorsAndExpiresOnItsDate() {
        var d = EditorDraft()
        d.name = "Once"; d.recipients = ["12345678"]; d.message = "Hi"
        d.oneOff = true; d.oneOffEpochDay = today.adding(days: 3).epochDay // Friday
        let m = d.toMacro(id: "o", today: today)
        #expect(!m.repeatDaily)
        #expect(m.daysOfWeek == [5])
        #expect(m.anchorEpochDay == today.adding(days: 3).epochDay)
        #expect(m.validUntilEpochDay == m.anchorEpochDay)
    }

    @Test func everyTwoWeeksAnchorsOnTheFirstSendDay() {
        var d = EditorDraft()
        d.name = "x"; d.recipients = ["12345678"]; d.message = "Hi"
        d.weekInterval = 2; d.daysOfWeek = [4]; d.startEpochDay = today.epochDay
        #expect(d.toMacro(id: "a", today: today).anchorEpochDay == today.adding(days: 2).epochDay) // Thursday
    }

    @Test func validation() {
        var d = EditorDraft()
        #expect(d.problems == ["Give the macro a name", "Add a recipient", "Write the message"])
        d.name = "x"; d.recipients = ["abc"]; d.message = "Hi"
        #expect(d.problems == ["Check the phone numbers"])
        d.triggerType = .incoming
        #expect(d.canSave) // replies answer the sender
        d.triggerType = .manual; d.actionType = .webhook; d.webhookUrl = "ftp://x"
        #expect(d.problems == ["Enter a URL starting with http:// or https://"])
        #expect(isValidPhone("+45 12 34 56 78") && !isValidPhone("45+12") && !isValidPhone("12"))
    }

    @Test func repliesDropRecipients() {
        var d = EditorDraft()
        d.name = "x"; d.recipients = ["12345678"]; d.message = "Hi"; d.triggerType = .incoming
        d.matchKeyword = " hello "
        let m = d.toMacro(id: "r", today: today)
        #expect(m.recipients.isEmpty && m.matchKeyword == "hello" && m.scheduledTime == nil)
    }
}

@Suite struct PlannerTests {
    func daily(_ id: String, _ time: String) -> Macro {
        Macro(id: id, name: id, triggerType: .scheduled, scheduledTime: time, recipients: ["12345678"], messageBody: "x")
    }

    @Test func upcomingFiresStepForward() {
        let fires = daily("a", "08:00").upcomingFires(after: at(2026, 11, 24, 9), count: 3, timeZone: cph)
        #expect(fires == [at(2026, 11, 25, 8), at(2026, 11, 26, 8), at(2026, 11, 27, 8)])
        var off = daily("b", "08:00"); off.enabled = false
        #expect(off.upcomingFires(after: at(2026, 11, 24, 9), count: 3, timeZone: cph).isEmpty)
    }

    @Test func oneOffFiresOnce() {
        var m = daily("o", "10:00")
        m.repeatDaily = false; m.anchorEpochDay = CivilDate(year: 2026, month: 11, day: 26).epochDay
        m.validUntilEpochDay = m.anchorEpochDay; m.daysOfWeek = [4]
        #expect(m.upcomingFires(after: at(2026, 11, 24, 9), count: 5, timeZone: cph) == [at(2026, 11, 26, 10)])
    }

    @Test func budgetGivesEveryMacroItsNextSend() {
        var busy = (0..<10).map { daily("busy\($0)", String(format: "%02d:00", 8 + $0)) }
        busy.append(daily("late", "23:30"))
        let plan = planFires(busy, after: at(2026, 11, 24, 7), budget: 11, timeZone: cph)
        #expect(plan.count == 11)
        #expect(plan.contains { $0.macroId == "late" })
        #expect(plan == plan.sorted { $0.at < $1.at })
        let p = plan[0]
        #expect(PlannedFire(id: p.id) == p)
    }
}

@Suite struct DueFireTests {
    @Test func listsWhatCameDue() {
        let m = Macro(id: "a", name: "a", triggerType: .scheduled, scheduledTime: "08:00", recipients: [], messageBody: "x")
        #expect(m.dueFires(since: at(2026, 11, 24, 9), until: at(2026, 11, 26, 8, 30), timeZone: cph)
                == [at(2026, 11, 25, 8), at(2026, 11, 26, 8)])
        #expect(m.dueFires(since: at(2026, 11, 1, 9), until: at(2026, 11, 26, 9), limit: 2, timeZone: cph).count == 2)
        #expect(m.dueFires(since: at(2026, 11, 26, 9), until: at(2026, 11, 26, 9), timeZone: cph).isEmpty)
    }
}

@Suite struct SharedStoreTests {
    @Test func readsTheAppFileAndPicksTheReply() throws {
        var reply = Macro(id: "r", name: "Away", triggerType: .incoming, recipients: [], messageBody: "Back Monday")
        reply.matchKeyword = "meeting"
        let other = Macro(id: "m", name: "Late", triggerType: .manual, recipients: ["1"], messageBody: "x")
        let json = try JSONEncoder().encode(["macros": [reply, other]])
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("tf-\(UUID()).json")
        try json.write(to: url)
        let stored = StoredMacros.load(from: url)
        #expect(stored.macros.count == 2)
        #expect(Macro.replyMacro(in: stored.macros, sender: "+4511", body: "About the meeting")?.id == "r")
        #expect(Macro.replyMacro(in: stored.macros, sender: "+4511", body: "Hi") == nil)
        #expect(StoredMacros.load(from: url.appendingPathExtension("missing")).macros.isEmpty)
    }
}
