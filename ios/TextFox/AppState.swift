import Foundation
import MessageUI
import Observation
import SwiftUI
import TextFoxCore
import UserNotifications

/// A short message at the bottom of the screen, optionally with an action (Undo).
struct Toast: Identifiable, Equatable {
    let id = UUID()
    let text: String
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil
    static func == (a: Toast, b: Toast) -> Bool { a.id == b.id }
}

/// Screen-level state shared by the tabs: the toast and the Messages sheet.
@MainActor @Observable
final class AppState {
    let store: MacroStore
    var toast: Toast?
    var outgoing: OutgoingMessage?
    var tab: Tab = .macros
    /// The open macro editor, if any (set by the list, the + button and textfox:// links).
    var editor: EditorTarget?

    init(store: MacroStore) { self.store = store }

    /// textfox://new, textfox://edit/<macro id or name>, textfox://log, textfox://settings.
    func open(_ url: URL) {
        guard url.scheme == "textfox" else { return }
        let rest = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).removingPercentEncoding ?? ""
        switch url.host() {
        case "new": tab = .macros; editor = .new
        case "edit":
            if let m = store.macros.first(where: { $0.id == rest || $0.name.caseInsensitiveCompare(rest) == .orderedSame }) {
                tab = .macros; editor = .edit(m.id)
            }
        case "log": tab = .log
        case "settings": tab = .settings
        default: tab = .macros
        }
    }

    func show(_ text: String, actionLabel: String? = nil, action: (() -> Void)? = nil) {
        let t = Toast(text: text, actionLabel: actionLabel, action: action)
        withAnimation(.spring(response: 0.35)) { toast = t }
        Task {
            try? await Task.sleep(for: .seconds(action == nil ? 2.5 : 5))
            if toast == t { withAnimation { toast = nil } }
        }
    }

    /// "Send now": opens the filled-in message for SMS, shows a reminder, or calls the webhook.
    func run(_ macro: Macro) {
        switch macro.actionType {
        case .sms:
            if macro.recipients.isEmpty {
                show(macro.triggerType == .incoming ? "Auto-replies answer whoever texted you" : "Add a recipient first")
                return
            }
            let message = OutgoingMessage(macro: macro)
            if OutgoingMessage.canSend {
                outgoing = message
            } else {
                store.record(macro, status: .failed, text: message.body,
                             detail: "This device can't send text messages")
                show("This device can't send texts — logged the message instead")
            }
        case .reminder:
            Task { await remind(macro) }
        case .webhook:
            Task { await callWebhook(macro) }
        case .sound:
            show("iPhone apps can't change the sound mode")
        }
    }

    func finishedSending(_ message: OutgoingMessage, _ result: MessageComposeResult) {
        outgoing = nil
        switch result {
        case .sent:
            store.record(message.macro, status: .success, text: message.body)
            show("Sent — \(message.macro.name)")
        case .failed:
            store.record(message.macro, status: .failed, text: message.body, detail: "Messages couldn't send it")
            show("Couldn't send — \(message.macro.name)")
        default:
            show("Not sent")
        }
    }

    private func remind(_ macro: Macro) async {
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        guard granted else { show("Allow notifications in Settings to get reminders"); return }
        let content = UNMutableNotificationContent()
        content.title = macro.name
        content.body = expandTemplate(macro.messageBody, now: Date(), macroName: macro.name, locale: Locale(identifier: "en_GB"))
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content,
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false))
        try? await center.add(request)
        store.record(macro, status: .success, text: content.body, detail: "Reminder")
        show("Reminder on its way — \(macro.name)")
    }

    private func callWebhook(_ macro: Macro) async {
        guard let s = macro.webhookUrl, let url = URL(string: s), url.scheme?.hasPrefix("http") == true else {
            show("Add a webhook URL first"); return
        }
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = macro.webhookMethod
        let body = expandTemplate(macro.messageBody, now: Date(), macroName: macro.name, locale: Locale(identifier: "en_GB"))
        if macro.webhookMethod == "POST" { request.httpBody = Data(body.utf8) }
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            let ok = (200..<300).contains(code)
            store.record(macro, status: ok ? .success : .failed, text: "\(macro.webhookMethod) \(s)", detail: "HTTP \(code)")
            show(ok ? "Webhook called — \(macro.name)" : "Webhook answered \(code)")
        } catch {
            store.record(macro, status: .failed, text: "\(macro.webhookMethod) \(s)", detail: error.localizedDescription)
            show("Webhook failed — \(error.localizedDescription)")
        }
    }
}
