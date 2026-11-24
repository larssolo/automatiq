import Foundation

/// The editor's form, separate from the saved macro: it keeps choices the macro doesn't store while
/// they're switched off (e.g. the random-days range), and maps back with the Android app's rules.
public struct EditorDraft: Equatable, Sendable {
    public var id: String?
    public var name = ""
    public var triggerType: TriggerType = .scheduled
    public var scheduledTime = "09:00"
    public var recipients: [String] = [""]
    public var message = ""
    public var enabled = true
    public var createdAt = Int64(Date().timeIntervalSince1970 * 1000)
    public var lastTriggeredAt: Int64?
    public var lastStatus: MacroStatus?
    public var lastScheduledFireAt: Int64?
    public var sortOrder = 0
    public var folderId: String?
    public var daysOfWeek: Set<Int> = allDays
    public var weekInterval = 1
    public var startEpochDay: Int?
    public var oneOff = false
    public var oneOffEpochDay: Int?
    public var randomSpreadEnabled = false
    public var randomSpreadMinutes = 5
    public var actionType: ActionType = .sms
    public var soundMode: SoundMode = .silent
    public var webhookUrl = ""
    public var webhookMethod = "GET"
    public var randomDaysEnabled = false
    public var randomDaysMin = 1
    public var randomDaysMax = 2
    public var validUntilEpochDay: Int?
    public var matchSender = ""
    public var matchKeyword = ""
    public var latitude: Double?
    public var longitude: Double?
    public var radiusMeters: Double = 200
    /// 1 = arrive, 2 = leave.
    public var geofenceTransition = 1
    public var cardColor: Int64 = cardColorPalette.randomElement()!
    public var aiReplyEnabled = false
    public var aiSendMode: AiSendMode = .approve
    public var aiReplyInstruction = ""
    public var triggerOnConnect = true
    public var triggerTarget = ""
    public var triggerTargetLabel = ""

    public init() {}

    public init(_ m: Macro) {
        let oneOff = m.triggerType == .scheduled && !m.repeatDaily
        id = m.id; name = m.name; triggerType = m.triggerType
        scheduledTime = m.scheduledTime ?? "09:00"
        recipients = m.recipients.isEmpty ? [""] : m.recipients
        message = m.messageBody; enabled = m.enabled; createdAt = m.createdAt
        lastTriggeredAt = m.lastTriggeredAt; lastStatus = m.lastStatus
        lastScheduledFireAt = m.lastScheduledFireAt; sortOrder = m.sortOrder; folderId = m.folderId
        daysOfWeek = Set(m.daysOfWeek); weekInterval = m.weekInterval; startEpochDay = m.anchorEpochDay
        self.oneOff = oneOff
        oneOffEpochDay = oneOff ? (m.anchorEpochDay ?? m.validUntilEpochDay) : nil
        randomSpreadEnabled = m.randomSpreadMinutes > 0
        randomSpreadMinutes = m.randomSpreadMinutes > 0 ? m.randomSpreadMinutes : 5
        actionType = m.actionType; soundMode = m.soundMode
        webhookUrl = m.webhookUrl ?? ""; webhookMethod = m.webhookMethod
        randomDaysEnabled = m.randomDaysMax > 0
        randomDaysMin = m.randomDaysMax > 0 ? m.randomDaysMin : 1
        randomDaysMax = m.randomDaysMax > 0 ? m.randomDaysMax : 2
        validUntilEpochDay = m.validUntilEpochDay
        matchSender = m.matchSender ?? ""; matchKeyword = m.matchKeyword ?? ""
        latitude = m.latitude; longitude = m.longitude; radiusMeters = m.radiusMeters ?? 200
        geofenceTransition = m.geofenceTransition ?? 1
        cardColor = m.cardColor == 0 ? m.accentColor : m.cardColor
        aiReplyEnabled = m.aiReplyEnabled; aiSendMode = m.aiSendMode
        aiReplyInstruction = m.aiReplyInstruction ?? ""
        triggerOnConnect = m.triggerOnConnect
        triggerTarget = m.triggerTarget ?? ""; triggerTargetLabel = m.triggerTargetLabel ?? ""
    }

    // MARK: Rules

    public var isReply: Bool { triggerType == .incoming || triggerType == .missedCall }
    /// Replies always answer by SMS; everything else does the chosen action.
    public var effectiveAction: ActionType { isReply ? .sms : actionType }
    public var cleanRecipients: [String] {
        recipients.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }
    public var recipientsRequired: Bool { effectiveAction == .sms && !isReply }
    /// AI writes auto-replies and varies scheduled texts.
    public var aiApplies: Bool { (triggerType == .incoming || triggerType == .scheduled) && effectiveAction == .sms }

    public var nameValid: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty }
    public var phoneValid: Bool { !cleanRecipients.isEmpty && cleanRecipients.allSatisfy(isValidPhone) }
    public var messageValid: Bool {
        !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (effectiveAction != .sms && effectiveAction != .reminder)
    }
    public var webhookValid: Bool {
        guard effectiveAction == .webhook else { return true }
        let u = webhookUrl.trimmingCharacters(in: .whitespaces).lowercased()
        return (u.hasPrefix("http://") || u.hasPrefix("https://")) && u.count > 8 && !u.contains(" ")
    }
    public var daysValid: Bool { triggerType != .scheduled || oneOff || !daysOfWeek.isEmpty }
    public var randomSpreadValid: Bool { !randomSpreadEnabled || randomSpreadMinutes >= 1 }
    public var randomDaysValid: Bool {
        !randomDaysEnabled || oneOff || triggerType != .scheduled
            || ((1...max(randomDaysMax, 1)).contains(randomDaysMin) && randomDaysMax <= daysOfWeek.count)
    }
    public var locationValid: Bool { triggerType != .location || (latitude != nil && longitude != nil) }

    /// What still blocks Save, in the order the form shows it; empty when it can be saved.
    public var problems: [String] {
        var p: [String] = []
        if !nameValid { p.append("Give the macro a name") }
        if !daysValid { p.append("Pick at least one day") }
        if !randomDaysValid { p.append("Select at least \(randomDaysMax) days for random days") }
        if !randomSpreadValid { p.append("Vary send time needs at least 1 minute") }
        if !locationValid { p.append("Choose a place") }
        if recipientsRequired && !phoneValid {
            p.append(cleanRecipients.isEmpty ? "Add a recipient" : "Check the phone numbers")
        }
        if !webhookValid { p.append("Enter a URL starting with http:// or https://") }
        if !messageValid { p.append("Write the message") }
        return p
    }
    public var canSave: Bool { problems.isEmpty }

    // MARK: Back to a macro

    public func toMacro(id: String, today: CivilDate) -> Macro {
        let scheduled = triggerType == .scheduled, incoming = triggerType == .incoming
        let location = triggerType == .location
        let oneOffDate: CivilDate? = scheduled && oneOff ? CivilDate(epochDay: oneOffEpochDay ?? today.epochDay) : nil
        let interval = scheduled && !oneOff ? max(weekInterval, 1) : 1
        let anchor: Int?
        if let d = oneOffDate { anchor = d.epochDay }
        else if scheduled && interval > 1 {
            // Anchor the rhythm on the first real send on or after the chosen start.
            let start = CivilDate(epochDay: startEpochDay ?? today.epochDay)
            anchor = (0..<7).map(start.adding).first { daysOfWeek.contains($0.isoWeekday) }?.epochDay ?? start.epochDay
        } else { anchor = nil }

        var m = Macro(id: id, name: name.trimmingCharacters(in: .whitespaces), triggerType: triggerType,
                      scheduledTime: scheduled ? scheduledTime : nil,
                      recipients: isReply || effectiveAction != .sms ? [] : cleanRecipients,
                      messageBody: message, createdAt: createdAt)
        m.repeatDaily = oneOffDate == nil
        m.enabled = enabled
        m.lastTriggeredAt = lastTriggeredAt; m.lastStatus = lastStatus; m.lastScheduledFireAt = lastScheduledFireAt
        m.sortOrder = sortOrder; m.folderId = folderId
        m.actionType = effectiveAction; m.soundMode = soundMode
        m.webhookUrl = effectiveAction == .webhook ? webhookUrl.trimmingCharacters(in: .whitespaces) : nil
        m.webhookMethod = webhookMethod
        m.daysOfWeek = oneOffDate.map { [$0.isoWeekday] } ?? (scheduled ? daysOfWeek.sorted() : allDays.sorted())
        m.weekInterval = interval
        m.anchorEpochDay = anchor
        m.randomSpreadMinutes = scheduled && randomSpreadEnabled ? max(randomSpreadMinutes, 0) : 0
        m.randomDaysMin = scheduled && !oneOff && randomDaysEnabled ? randomDaysMin : 0
        m.randomDaysMax = scheduled && !oneOff && randomDaysEnabled ? randomDaysMax : 0
        m.cardColor = cardColor
        m.aiReplyEnabled = aiApplies && aiReplyEnabled
        m.aiSendMode = aiSendMode
        let instruction = aiReplyInstruction.trimmingCharacters(in: .whitespacesAndNewlines)
        m.aiReplyInstruction = m.aiReplyEnabled && !instruction.isEmpty ? instruction : nil
        let stateTrigger = [.charging, .bluetooth, .wifi].contains(triggerType)
        m.triggerOnConnect = stateTrigger ? triggerOnConnect : true
        let targeted = triggerType == .bluetooth || triggerType == .wifi
        m.triggerTarget = targeted && !triggerTarget.isEmpty ? triggerTarget : nil
        m.triggerTargetLabel = targeted && !triggerTargetLabel.isEmpty ? triggerTargetLabel : nil
        m.validUntilEpochDay = oneOffDate?.epochDay ?? (scheduled ? validUntilEpochDay : nil)
        let sender = matchSender.trimmingCharacters(in: .whitespaces)
        m.matchSender = isReply && !sender.isEmpty ? sender : nil
        let keyword = matchKeyword.trimmingCharacters(in: .whitespaces)
        m.matchKeyword = incoming && !keyword.isEmpty ? keyword : nil
        m.latitude = location ? latitude : nil
        m.longitude = location ? longitude : nil
        m.radiusMeters = location ? radiusMeters : nil
        m.geofenceTransition = location ? geofenceTransition : nil
        return m
    }
}

/// A phone number as people type them: digits with optional +, spaces, dashes and brackets, 3–15 digits.
public func isValidPhone(_ s: String) -> Bool {
    let allowed = CharacterSet(charactersIn: "0123456789+-() ")
    guard s.unicodeScalars.allSatisfy(allowed.contains) else { return false }
    if let plus = s.firstIndex(of: "+"), plus != s.startIndex { return false }
    return (3...15).contains(s.filter(\.isNumber).count)
}
