import Foundation

// The macro model, field for field the Android app's, so a backup exported on Android imports here.
// Raw values are the Android enum names, which is what the JSON files carry.

public enum TriggerType: String, Codable, Sendable, CaseIterable {
    case scheduled = "SCHEDULED", manual = "MANUAL", incoming = "INCOMING", location = "LOCATION"
    case charging = "CHARGING", bluetooth = "BLUETOOTH", wifi = "WIFI", missedCall = "MISSED_CALL"

    /// What the iPhone version can run. Missed calls, charging, Bluetooth and Wi-Fi have no app hook on
    /// iOS (Wi-Fi and charging only through Shortcuts automations, see docs/ios/PLAN.md).
    public var supportedOnIOS: Bool { [.scheduled, .manual, .incoming, .location].contains(self) }
}

public enum MacroStatus: String, Codable, Sendable { case success = "SUCCESS", failed = "FAILED", pending = "PENDING" }
public enum AiSendMode: String, Codable, Sendable { case approve = "APPROVE", auto = "AUTO" }
public enum ActionType: String, Codable, Sendable { case sms = "SMS", sound = "SOUND", reminder = "REMINDER", webhook = "WEBHOOK" }
public enum SoundMode: String, Codable, Sendable {
    case normal = "NORMAL", vibrate = "VIBRATE", silent = "SILENT", dndOn = "DND_ON", dndOff = "DND_OFF"
}

public struct Macro: Codable, Identifiable, Hashable, Sendable {
    public var id: String
    public var name: String
    public var triggerType: TriggerType
    /// "HH:mm" for scheduled macros.
    public var scheduledTime: String?
    public var repeatDaily: Bool = true
    public var recipients: [String]
    public var messageBody: String
    public var enabled: Bool = true
    public var lastTriggeredAt: Int64?
    public var lastStatus: MacroStatus?
    public var createdAt: Int64
    public var lastScheduledFireAt: Int64?
    public var sortOrder: Int = 0
    /// ISO weekdays, 1 = Monday … 7 = Sunday.
    public var daysOfWeek: [Int] = [1, 2, 3, 4, 5, 6, 7]
    public var weekInterval: Int = 1
    public var anchorEpochDay: Int?
    public var randomSpreadMinutes: Int = 0
    public var randomDaysMin: Int = 0
    public var randomDaysMax: Int = 0
    /// ARGB, as on Android. 0 = not assigned yet.
    public var cardColor: Int64 = 0
    public var validUntilEpochDay: Int?
    public var matchSender: String?
    public var matchKeyword: String?
    public var latitude: Double?
    public var longitude: Double?
    public var radiusMeters: Double?
    /// 1 = arrive, 2 = leave.
    public var geofenceTransition: Int?
    public var aiReplyEnabled: Bool = false
    public var aiSendMode: AiSendMode = .approve
    public var aiReplyInstruction: String?
    public var triggerOnConnect: Bool = true
    public var triggerTarget: String?
    public var triggerTargetLabel: String?
    public var folderId: String?
    public var actionType: ActionType = .sms
    public var soundMode: SoundMode = .silent
    public var webhookUrl: String?
    public var webhookMethod: String = "GET"

    public init(
        id: String = UUID().uuidString, name: String, triggerType: TriggerType,
        scheduledTime: String? = nil, recipients: [String], messageBody: String,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id; self.name = name; self.triggerType = triggerType
        self.scheduledTime = scheduledTime; self.recipients = recipients
        self.messageBody = messageBody; self.createdAt = createdAt
    }

    /// The macro's random-days setting, or nil when it sends on every selected day.
    public var randomDays: RandomDays? {
        randomDaysMax > 0 ? RandomDays(seed: id, min: randomDaysMin, max: randomDaysMax) : nil
    }

    enum CodingKeys: String, CodingKey {
        case id, name, triggerType, scheduledTime, repeatDaily, recipientNumber, recipients, messageBody
        case enabled, lastTriggeredAt, lastStatus, createdAt, lastScheduledFireAt, sortOrder, daysOfWeek
        case weekInterval, anchorEpochDay, randomSpreadMinutes, randomDaysMin, randomDaysMax, cardColor
        case validUntilEpochDay, matchSender, matchKeyword, latitude, longitude, radiusMeters
        case geofenceTransition, aiReplyEnabled, aiSendMode, aiReplyInstruction, triggerOnConnect
        case triggerTarget, triggerTargetLabel, folderId, actionType, soundMode, webhookUrl, webhookMethod
    }

    /// Lenient like the Android importer: missing fields take their defaults, unknown fields are
    /// ignored, old single-number files fall back to `recipientNumber`, and a malformed time is an error.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func opt<T: Decodable>(_ k: CodingKeys) throws -> T? { try c.decodeIfPresent(T.self, forKey: k) }
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        triggerType = try c.decode(TriggerType.self, forKey: .triggerType)
        scheduledTime = try opt(.scheduledTime)
        if let t = scheduledTime, HhMm(t) == nil {
            throw DecodingError.dataCorruptedError(forKey: .scheduledTime, in: c,
                debugDescription: "invalid scheduledTime \"\(t)\" for macro \"\(name)\"")
        }
        repeatDaily = try opt(.repeatDaily) ?? true
        let list: [String] = try opt(.recipients) ?? []
        let legacy: String? = try opt(.recipientNumber)
        recipients = list.isEmpty ? [legacy].compactMap { $0 } : list
        messageBody = try c.decode(String.self, forKey: .messageBody)
        enabled = try opt(.enabled) ?? true
        lastTriggeredAt = try opt(.lastTriggeredAt)
        lastStatus = try opt(.lastStatus)
        createdAt = try opt(.createdAt) ?? 0
        lastScheduledFireAt = try opt(.lastScheduledFireAt)
        sortOrder = try opt(.sortOrder) ?? 0
        daysOfWeek = try opt(.daysOfWeek) ?? [1, 2, 3, 4, 5, 6, 7]
        weekInterval = try opt(.weekInterval) ?? 1
        anchorEpochDay = try opt(.anchorEpochDay)
        randomSpreadMinutes = try opt(.randomSpreadMinutes) ?? 0
        randomDaysMin = try opt(.randomDaysMin) ?? 0
        randomDaysMax = try opt(.randomDaysMax) ?? 0
        cardColor = try opt(.cardColor) ?? 0
        validUntilEpochDay = try opt(.validUntilEpochDay)
        matchSender = try opt(.matchSender)
        matchKeyword = try opt(.matchKeyword)
        latitude = try opt(.latitude)
        longitude = try opt(.longitude)
        radiusMeters = try opt(.radiusMeters)
        geofenceTransition = try opt(.geofenceTransition)
        aiReplyEnabled = try opt(.aiReplyEnabled) ?? false
        aiSendMode = try opt(.aiSendMode) ?? .approve
        aiReplyInstruction = try opt(.aiReplyInstruction)
        triggerOnConnect = try opt(.triggerOnConnect) ?? true
        triggerTarget = try opt(.triggerTarget)
        triggerTargetLabel = try opt(.triggerTargetLabel)
        folderId = try opt(.folderId)
        actionType = (try? c.decodeIfPresent(ActionType.self, forKey: .actionType)) ?? .sms
        soundMode = (try? c.decodeIfPresent(SoundMode.self, forKey: .soundMode)) ?? .silent
        webhookUrl = try opt(.webhookUrl)
        webhookMethod = try opt(.webhookMethod) ?? "GET"
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(name, forKey: .name)
        try c.encode(triggerType, forKey: .triggerType); try c.encode(scheduledTime, forKey: .scheduledTime)
        try c.encode(repeatDaily, forKey: .repeatDaily); try c.encode(recipients, forKey: .recipients)
        try c.encode(messageBody, forKey: .messageBody); try c.encode(enabled, forKey: .enabled)
        try c.encode(lastTriggeredAt, forKey: .lastTriggeredAt); try c.encode(lastStatus, forKey: .lastStatus)
        try c.encode(createdAt, forKey: .createdAt); try c.encode(lastScheduledFireAt, forKey: .lastScheduledFireAt)
        try c.encode(sortOrder, forKey: .sortOrder); try c.encode(daysOfWeek.sorted(), forKey: .daysOfWeek)
        try c.encode(weekInterval, forKey: .weekInterval); try c.encode(anchorEpochDay, forKey: .anchorEpochDay)
        try c.encode(randomSpreadMinutes, forKey: .randomSpreadMinutes)
        try c.encode(randomDaysMin, forKey: .randomDaysMin); try c.encode(randomDaysMax, forKey: .randomDaysMax)
        try c.encode(cardColor, forKey: .cardColor); try c.encode(validUntilEpochDay, forKey: .validUntilEpochDay)
        try c.encode(matchSender, forKey: .matchSender); try c.encode(matchKeyword, forKey: .matchKeyword)
        try c.encode(latitude, forKey: .latitude); try c.encode(longitude, forKey: .longitude)
        try c.encode(radiusMeters, forKey: .radiusMeters); try c.encode(geofenceTransition, forKey: .geofenceTransition)
        try c.encode(aiReplyEnabled, forKey: .aiReplyEnabled); try c.encode(aiSendMode, forKey: .aiSendMode)
        try c.encode(aiReplyInstruction, forKey: .aiReplyInstruction)
        try c.encode(triggerOnConnect, forKey: .triggerOnConnect); try c.encode(triggerTarget, forKey: .triggerTarget)
        try c.encode(triggerTargetLabel, forKey: .triggerTargetLabel); try c.encode(folderId, forKey: .folderId)
        try c.encode(actionType, forKey: .actionType); try c.encode(soundMode, forKey: .soundMode)
        try c.encode(webhookUrl, forKey: .webhookUrl); try c.encode(webhookMethod, forKey: .webhookMethod)
    }
}

public struct Folder: Codable, Identifiable, Hashable, Sendable {
    public var id: String
    public var name: String
    public var cardColor: Int64 = 0
    public var sortOrder: Int = 0
    public var expanded: Bool = true
    public var createdAt: Int64 = 0

    public init(id: String = UUID().uuidString, name: String) { self.id = id; self.name = name }

    enum CodingKeys: String, CodingKey { case id, name, cardColor, sortOrder, expanded, createdAt }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        cardColor = try c.decodeIfPresent(Int64.self, forKey: .cardColor) ?? 0
        sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        expanded = try c.decodeIfPresent(Bool.self, forKey: .expanded) ?? true
        createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
    }
}

/// The Android full-backup file: macros, folders and a flat settings map.
public struct Backup: Codable, Sendable {
    public var version: Int = 1
    public var macros: [Macro] = []
    public var folders: [Folder] = []
    public var settings: [String: String] = [:]

    public init(macros: [Macro], folders: [Folder], settings: [String: String]) {
        self.macros = macros; self.folders = folders; self.settings = settings
    }

    enum CodingKeys: String, CodingKey { case version, macros, folders, settings }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        macros = try c.decodeIfPresent([Macro].self, forKey: .macros) ?? []
        folders = try c.decodeIfPresent([Folder].self, forKey: .folders) ?? []
        settings = try c.decodeIfPresent([String: String].self, forKey: .settings) ?? [:]
    }

    /// Reads a full backup, or an older macro-only file (a bare JSON array of macros).
    public static func parse(_ data: Data) throws -> Backup {
        let decoder = JSONDecoder()
        if let macros = try? decoder.decode([Macro].self, from: data) {
            return Backup(macros: macros, folders: [], settings: [:])
        }
        return try decoder.decode(Backup.self, from: data)
    }
}
