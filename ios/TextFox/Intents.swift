import AppIntents
import Foundation
import TextFoxCore

// The actions TextFox gives the Shortcuts app. A Shortcuts automation ("Every day at 8:00",
// "When I get a message from…") can run them and pass the text to Shortcuts' own Send Message,
// which sends without asking — the way to fully automatic texts on iPhone.

/// "Get TextFox Message": the macro's text with the variables filled in for now.
struct GetMessageIntent: AppIntent {
    static let title: LocalizedStringResource = "Get TextFox Message"
    static let description = IntentDescription("Gives the message of a TextFox macro, with the date, time and other variables filled in. Pass it to Send Message.")

    @Parameter(title: "Macro") var macro: MacroEntity

    static var parameterSummary: some ParameterSummary { Summary("Get the message of \(\.$macro)") }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        guard let m = MacroStore.shared.macro(id: macro.id) else { throw IntentError.macroGone }
        guard Purchases.shared.access.canRun else { throw IntentError.locked }
        let (text, note) = await AI.text(for: m)
        MacroStore.shared.record(m, status: .success, text: text, detail: ["Handed to Shortcuts", note].compactMap { $0 }.joined(separator: " · "))
        return .result(value: text)
    }
}

/// "Get TextFox Recipients": the macro's phone numbers, for Send Message's recipients.
struct GetRecipientsIntent: AppIntent {
    static let title: LocalizedStringResource = "Get TextFox Recipients"
    static let description = IntentDescription("Gives the phone numbers a TextFox macro sends to.")

    @Parameter(title: "Macro") var macro: MacroEntity

    static var parameterSummary: some ParameterSummary { Summary("Get the recipients of \(\.$macro)") }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<[String]> {
        guard let m = MacroStore.shared.macro(id: macro.id) else { throw IntentError.macroGone }
        return .result(value: m.recipients)
    }
}

/// "TextFox Auto-Reply": the reply for an incoming text, or nothing when no auto-reply macro matches
/// (or quiet hours are on). Use it in a "When I get a message" automation.
struct AutoReplyIntent: AppIntent {
    static let title: LocalizedStringResource = "Get TextFox Auto-Reply"
    static let description = IntentDescription("Gives the reply for an incoming message from your TextFox auto-reply macros. Empty when none matches, so you can check it with If.")

    @Parameter(title: "Sender") var sender: String
    @Parameter(title: "Message", default: "") var message: String

    static var parameterSummary: some ParameterSummary { Summary("Get the reply to \(\.$message) from \(\.$sender)") }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let store = MacroStore.shared
        guard Purchases.shared.access.canRun else { return .result(value: "") }
        guard let m = Macro.replyMacro(in: store.macros, sender: sender, body: message) else { return .result(value: "") }
        let s = store.settings
        let minute = Calendar.current.component(.hour, from: Date()) * 60 + Calendar.current.component(.minute, from: Date())
        if s.quietHoursEnabled && isWithinQuietHours(minute, start: s.quietStart, end: s.quietEnd) {
            store.record(m, status: .pending, text: m.messageBody, detail: "Quiet hours — not answered")
            return .result(value: "")
        }
        let (text, note) = await AI.reply(for: m, sender: sender, incoming: message)
        store.record(m, status: .success, text: text,
                     detail: ["Reply to \(maskPhone(sender)) handed to Shortcuts", note].compactMap { $0 }.joined(separator: " · "))
        return .result(value: text)
    }
}

/// "Run TextFox Macro": opens TextFox and runs the macro (for SMS: the finished message, ready to send).
struct RunMacroIntent: AppIntent {
    static let title: LocalizedStringResource = "Run TextFox Macro"
    static let description = IntentDescription("Opens TextFox with the macro's message ready to send.")
    static let openAppWhenRun = true

    @Parameter(title: "Macro") var macro: MacroEntity

    static var parameterSummary: some ParameterSummary { Summary("Run \(\.$macro)") }

    @MainActor
    func perform() async throws -> some IntentResult {
        guard let m = MacroStore.shared.macro(id: macro.id) else { throw IntentError.macroGone }
        AppState.shared.tab = .macros
        AppState.shared.run(m)
        return .result()
    }
}

enum IntentError: Error, CustomLocalizedStringResourceConvertible {
    case macroGone, locked
    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .macroGone: "That macro no longer exists in TextFox."
        case .locked: "TextFox's free trial isn't running. Open TextFox to start it or unlock."
        }
    }
}

struct TextFoxShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: RunMacroIntent(), phrases: ["Run \(\.$macro) in \(.applicationName)", "\(.applicationName) \(\.$macro)"],
                    shortTitle: "Run Macro", systemImageName: "bolt.fill")
        AppShortcut(intent: GetMessageIntent(), phrases: ["Get the \(.applicationName) message"],
                    shortTitle: "Get Message", systemImageName: "text.bubble")
    }

    static let shortcutTileColor = ShortcutTileColor.lime
}
