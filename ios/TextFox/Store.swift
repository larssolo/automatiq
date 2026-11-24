import Foundation
import Observation
import TextFoxCore

/// One line in the log: what ran, when, and how it went.
struct LogEntry: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var macroId: String
    var macroName: String
    var at: Date
    var status: MacroStatus
    var text: String
    var detail: String?
    /// The planned send this entry is about, so tapping Send later updates the same line.
    var fireId: String?
}

/// App-wide settings that live next to the macros (and travel in the backup's settings map).
struct AppSettings: Codable, Hashable {
    var quietHoursEnabled = false
    /// Minutes after midnight.
    var quietStart = 22 * 60
    var quietEnd = 7 * 60
}

/// Everything the app stores, as one JSON file in the App Group so the widget and the Shortcuts
/// actions can read the same macros. The macros and folders use the Android backup format.
@MainActor @Observable
final class MacroStore {
    private(set) var macros: [Macro] = []
    private(set) var folders: [Folder] = []
    private(set) var log: [LogEntry] = []
    var settings = AppSettings() { didSet { save() } }

    private struct Snapshot: Codable {
        var macros: [Macro]
        var folders: [Folder]
        var log: [LogEntry]
        var settings: AppSettings
    }

    private let url: URL

    init(fileName: String = "textfox.json") {
        let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.app.kodekid.textfox")
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        url = dir.appendingPathComponent(fileName)
        #if DEBUG
        // UI tests start from the demo data every time.
        if UserDefaults.standard.bool(forKey: "resetData") { try? FileManager.default.removeItem(at: url) }
        #endif
        load()
    }

    var rows: [ListRow] { flattenRows(folders: folders, macros: macros) }
    var activeCount: Int { macros.filter(\.enabled).count }

    // MARK: Persistence

    private func load() {
        guard let data = try? Data(contentsOf: url) else {
            #if DEBUG
            seedDemo()
            #endif
            return
        }
        do {
            let s = try JSONDecoder().decode(Snapshot.self, from: data)
            macros = s.macros; folders = s.folders; log = s.log; settings = s.settings
        } catch {
            // Never overwrite a file we couldn't read: keep it aside and start empty.
            try? FileManager.default.moveItem(at: url, to: url.appendingPathExtension("unreadable-\(Int(Date().timeIntervalSince1970))"))
        }
    }

    private func save() {
        let s = Snapshot(macros: macros, folders: folders, log: Array(log.prefix(500)), settings: settings)
        do {
            let data = try JSONEncoder().encode(s)
            try data.write(to: url, options: .atomic)
        } catch {
            assertionFailure("save failed: \(error)")
        }
    }

    // MARK: Macros

    func macro(id: String) -> Macro? { macros.first { $0.id == id } }

    /// Insert or replace. A new macro goes to the top of the list (or of its folder).
    func upsert(_ macro: Macro) {
        var m = macro
        m.lastScheduledFireAt = Int64(Date().timeIntervalSince1970 * 1000)
        if let i = macros.firstIndex(where: { $0.id == m.id }) {
            macros[i] = m
        } else {
            if m.cardColor == 0 { m.cardColor = cardColorPalette.randomElement()! }
            m.sortOrder = minOrder(inFolder: m.folderId) - 1
            macros.append(m)
        }
        save()
    }

    func delete(_ macro: Macro) {
        macros.removeAll { $0.id == macro.id }
        save()
    }

    /// Brings back a deleted macro exactly as it was.
    func restore(_ macro: Macro) {
        macros.removeAll { $0.id == macro.id }
        macros.append(macro)
        save()
    }

    func duplicate(_ macro: Macro) {
        var copy = macro
        copy.id = UUID().uuidString
        copy.name = macro.name + " (copy)"
        copy.lastTriggeredAt = nil
        copy.lastStatus = nil
        copy.createdAt = Int64(Date().timeIntervalSince1970 * 1000)
        copy.lastScheduledFireAt = copy.createdAt
        // Right below the original.
        copy.sortOrder = macro.sortOrder
        macros.append(copy)
        save()
    }

    func setEnabled(_ macro: Macro, _ on: Bool) {
        guard let i = macros.firstIndex(where: { $0.id == macro.id }) else { return }
        macros[i].enabled = on
        if on { macros[i].lastScheduledFireAt = Int64(Date().timeIntervalSince1970 * 1000) }
        save()
    }

    func move(_ macro: Macro, toFolder folderId: String?) {
        guard let i = macros.firstIndex(where: { $0.id == macro.id }) else { return }
        macros[i].folderId = folderId
        macros[i].sortOrder = minOrder(inFolder: folderId) - 1
        save()
    }

    private func minOrder(inFolder folderId: String?) -> Int {
        let inSpace = macros.filter { $0.folderId == folderId }.map(\.sortOrder)
        let folderOrders = folderId == nil ? folders.map(\.sortOrder) : []
        return (inSpace + folderOrders).min() ?? 0
    }

    // MARK: Folders

    func createFolder(named name: String) {
        var f = Folder(name: name.trimmingCharacters(in: .whitespaces))
        f.cardColor = cardColorPalette.randomElement()!
        f.sortOrder = minOrder(inFolder: nil) - 1
        f.createdAt = Int64(Date().timeIntervalSince1970 * 1000)
        folders.append(f)
        save()
    }

    func rename(_ folder: Folder, to name: String) {
        guard let i = folders.firstIndex(where: { $0.id == folder.id }) else { return }
        folders[i].name = name.trimmingCharacters(in: .whitespaces)
        save()
    }

    func toggleExpanded(_ folder: Folder) {
        guard let i = folders.firstIndex(where: { $0.id == folder.id }) else { return }
        folders[i].expanded.toggle()
        save()
    }

    func setAllEnabled(in folder: Folder, _ on: Bool) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        for i in macros.indices where macros[i].folderId == folder.id {
            if on && !macros[i].enabled { macros[i].lastScheduledFireAt = now }
            macros[i].enabled = on
        }
        save()
    }

    /// Deletes the folder; its macros move out to the root. Returns them for undo.
    @discardableResult
    func delete(_ folder: Folder) -> [String] {
        let members = macros.filter { $0.folderId == folder.id }.map(\.id)
        for i in macros.indices where macros[i].folderId == folder.id { macros[i].folderId = nil }
        folders.removeAll { $0.id == folder.id }
        save()
        return members
    }

    func restore(_ folder: Folder, members: [String]) {
        folders.append(folder)
        for i in macros.indices where members.contains(macros[i].id) { macros[i].folderId = folder.id }
        save()
    }

    // MARK: Reordering

    /// Applies a move in the flat row list. A moved macro joins the folder above it (or the root);
    /// a moved folder takes its members along.
    func moveRows(from source: IndexSet, to destination: Int) {
        var rows = self.rows
        guard let from = source.first else { return }
        let moved = rows[from]
        if case .folder(let f, _, _) = moved {
            // Lift the folder with its visible members, then drop the block at the destination.
            let block = [moved] + rows.enumerated().filter { i, r in
                if case .macro(let m) = r { return i > from && m.folderId == f.id } else { return false }
            }.map(\.element)
            let blockIds = Set(block.map(\.id))
            let before = rows[..<min(destination, rows.count)].filter { !blockIds.contains($0.id) }.count
            rows.removeAll { blockIds.contains($0.id) }
            // Never land inside another folder's member run: step past its members.
            var at = before
            while at < rows.count, at > 0, case .macro(let m) = rows[at], m.folderId != nil,
                  folderForDrop(rows: rows, at: at) != nil { at += 1 }
            rows.insert(contentsOf: block, at: at)
        } else if case .macro(var m) = moved {
            rows.move(fromOffsets: source, toOffset: destination)
            let at = rows.firstIndex { $0.id == moved.id }!
            m.folderId = folderForDrop(rows: rows, at: at)
            rows[at] = .macro(m)
            if let i = macros.firstIndex(where: { $0.id == m.id }) { macros[i].folderId = m.folderId }
        }
        apply(layoutOrders(rows))
    }

    private func apply(_ o: LayoutOrders) {
        for i in folders.indices { if let n = o.folderOrder[folders[i].id] { folders[i].sortOrder = n } }
        for i in macros.indices {
            let m = macros[i]
            if let fid = m.folderId, let n = o.memberOrder[fid]?[m.id] { macros[i].sortOrder = n }
            else if m.folderId == nil, let n = o.rootMacroOrder[m.id] { macros[i].sortOrder = n }
        }
        save()
    }

    // MARK: Log

    /// Adds a log line, or updates the line for the same planned send.
    func record(_ macro: Macro, status: MacroStatus, text: String, detail: String? = nil,
                at: Date = Date(), fireId: String? = nil) {
        let entry = LogEntry(macroId: macro.id, macroName: macro.name, at: at, status: status, text: text, detail: detail, fireId: fireId)
        if let fireId, let i = log.firstIndex(where: { $0.fireId == fireId }) {
            log[i] = LogEntry(id: log[i].id, macroId: entry.macroId, macroName: entry.macroName, at: log[i].at,
                              status: status, text: text, detail: detail, fireId: fireId)
        } else {
            log.insert(entry, at: 0)
            log.sort { $0.at > $1.at }
        }
        if let i = macros.firstIndex(where: { $0.id == macro.id }) {
            macros[i].lastTriggeredAt = Int64(Date().timeIntervalSince1970 * 1000)
            macros[i].lastStatus = status
        }
        save()
    }

    func clearLog() { log.removeAll(); save() }

    /// Scheduled sends up to [at] are handled (logged); the catch-up starts after this next time.
    func markScheduled(_ macro: Macro, at: Date) {
        guard let i = macros.firstIndex(where: { $0.id == macro.id }) else { return }
        macros[i].lastScheduledFireAt = Int64(at.timeIntervalSince1970 * 1000)
        save()
    }

    // MARK: Backup

    func backupData() throws -> Data {
        let b = Backup(macros: macros, folders: folders, settings: [
            "quiet_hours_enabled": String(settings.quietHoursEnabled),
            "quiet_start": String(settings.quietStart),
            "quiet_end": String(settings.quietEnd),
        ])
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try enc.encode(b)
    }

    /// Adds the backup's macros and folders; ones that already exist (same id) are replaced.
    /// Returns how many macros were imported.
    @discardableResult
    func importBackup(_ data: Data) throws -> Int {
        let b = try Backup.parse(data)
        let macroIds = Set(b.macros.map(\.id)), folderIds = Set(b.folders.map(\.id))
        macros.removeAll { macroIds.contains($0.id) }
        folders.removeAll { folderIds.contains($0.id) }
        macros += b.macros
        folders += b.folders
        if let v = b.settings["quiet_hours_enabled"].flatMap(Bool.init) { settings.quietHoursEnabled = v }
        if let v = b.settings["quiet_start"].flatMap(Int.init) { settings.quietStart = v }
        if let v = b.settings["quiet_end"].flatMap(Int.init) { settings.quietEnd = v }
        save()
        return b.macros.count
    }

    // MARK: Demo data (debug builds only, first launch)

    #if DEBUG
    private func seedDemo() {
        var family = Folder(name: "Family"); family.cardColor = 0xFFFF_9800; family.sortOrder = 0
        var work = Folder(name: "Work"); work.cardColor = 0xFF3F_51B5; work.sortOrder = 1

        var mom = Macro(name: "Good morning, Mom", triggerType: .scheduled, scheduledTime: "08:00",
                        recipients: ["+4512345601"], messageBody: "Good morning! ☀️ Have a lovely {ugedag}.")
        mom.daysOfWeek = [1, 2, 3, 4, 5]; mom.randomDaysMin = 1; mom.randomDaysMax = 2
        mom.aiReplyEnabled = true; mom.aiReplyInstruction = "warm and short, vary the wording"
        mom.folderId = family.id; mom.cardColor = 0xFFFF_9800; mom.sortOrder = 0

        var bed = Macro(name: "Kids' bedtime", triggerType: .scheduled, scheduledTime: "20:15",
                        recipients: ["+4512345602"], messageBody: "Bedtime in 15 min 🌙 Teeth, pyjamas, lights out.")
        bed.daysOfWeek = [1, 2, 3, 4]; bed.weekInterval = 2
        bed.anchorEpochDay = CivilDate(Date(), in: .current).monday.epochDay
        bed.folderId = family.id; bed.cardColor = 0xFF3F_51B5; bed.sortOrder = 1

        var late = Macro(name: "Running late", triggerType: .manual, recipients: ["+4512345603"],
                         messageBody: "Running about 10 minutes late, sorry!")
        late.folderId = work.id; late.cardColor = 0xFFE9_1E63; late.sortOrder = 0

        var away = Macro(name: "Out of office", triggerType: .incoming, recipients: [],
                         messageBody: "Hi! I'm out of the office and back on Monday.")
        away.aiReplyEnabled = true; away.folderId = work.id; away.cardColor = 0xFF00_9688; away.sortOrder = 1

        var home = Macro(name: "Home safe", triggerType: .location, recipients: ["+4512345604"],
                         messageBody: "Home safe 🏡")
        home.latitude = 55.6761; home.longitude = 12.5683; home.radiusMeters = 150; home.geofenceTransition = 1
        home.cardColor = 0xFF4C_AF50; home.sortOrder = 2

        var pills = Macro(name: "Pills", triggerType: .scheduled, scheduledTime: "09:00", recipients: [],
                          messageBody: "Reminder: take your vitamins 💊")
        pills.actionType = .reminder; pills.cardColor = 0xFF9C_27B0; pills.sortOrder = 3; pills.enabled = false

        folders = [family, work]
        macros = [mom, bed, late, away, home, pills]
        save()
    }
    #endif
}
