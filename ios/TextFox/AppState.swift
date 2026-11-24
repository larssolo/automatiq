import Foundation
import MessageUI
import Observation
import SwiftUI
import TextFoxCore
import UserNotifications
import UIKit

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
    static let shared = AppState(store: .shared)
    let store: MacroStore
    var toast: Toast?
    var outgoing: OutgoingMessage?
    var tab: Tab = .macros
    /// Whether TextFox may show notifications; scheduled macros need it.
    var notificationStatus: UNAuthorizationStatus = .notDetermined
    /// Debug: shows the widget's small and medium looks in a sheet.
    var showWidgetPreview = false
    /// The open macro editor, if any (set by the list, the + button and textfox:// links).
    var editor: EditorTarget?

    init(store: MacroStore) { self.store = store }

    /// textfox://new, textfox://edit/<macro id or name>, textfox://log, textfox://settings.
    func open(_ url: URL) {
        #if DEBUG
        NSLog("TFLINK open %@", url.absoluteString)
        #endif
        guard url.scheme == "textfox" else { return }
        let rest = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).removingPercentEncoding ?? ""
        switch url.host() {
        case "new": tab = .macros; editor = .new
        case "edit":
            if let m = store.macros.first(where: { $0.id == rest || $0.name.caseInsensitiveCompare(rest) == .orderedSame }) {
                tab = .macros; editor = .edit(m.id)
            }
        #if DEBUG
        case "test-fire":
            if let m = store.macros.first(where: { $0.id == rest || $0.name.caseInsensitiveCompare(rest) == .orderedSame }) {
                Task { await Scheduler.testFire(m) }
            }
        case "widget-preview": showWidgetPreview = true
        case "test-tap", "test-skip":
            // What a tap on Send (or Skip) in a notification does, without the lock screen.
            if let m = store.macros.first(where: { $0.id == rest || $0.name.caseInsensitiveCompare(rest) == .orderedSame }) {
                let at = m.nextFire() ?? Date()
                handle(NotificationTap(action: url.host() == "test-tap" ? Scheduler.sendAction : Scheduler.skipAction,
                                       fireId: PlannedFire(macroId: m.id, at: at).id, macroId: m.id, at: at))
            }
        #endif
        case "run":
            // From the widget: run the macro (for SMS this opens the filled-in message).
            if let m = store.macro(id: rest) { tab = .macros; run(m) }
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
            compose(macro, at: Date(), fireId: nil)
        case .reminder:
            Task { await remind(macro) }
        case .webhook:
            Task { await callWebhook(macro) }
        case .sound:
            show("iPhone apps can't change the sound mode")
        }
    }

    /// Opens the Messages sheet with the macro's text (an AI variation when it's on).
    private func compose(_ macro: Macro, at: Date, fireId: String?) {
        Task {
            if macro.aiReplyEnabled && AI.ready { show("Writing AI message — \(macro.name)…") }
            let (text, note) = await AI.text(for: macro, at: at)
            var message = OutgoingMessage(macro: macro, body: text, fireId: fireId)
            message.note = note
            if OutgoingMessage.canSend {
                outgoing = message
            } else {
                store.record(macro, status: .failed, text: text,
                             detail: ["This device can't send text messages", note].compactMap { $0 }.joined(separator: " · "),
                             at: at, fireId: fireId)
                show("This device can't send texts — logged the message instead")
            }
        }
    }

    func finishedSending(_ message: OutgoingMessage, _ result: MessageComposeResult) {
        outgoing = nil
        switch result {
        case .sent:
            store.record(message.macro, status: .success, text: message.body, detail: message.note, fireId: message.fireId)
            show("Sent — \(message.macro.name)")
        case .failed:
            store.record(message.macro, status: .failed, text: message.body, detail: "Messages couldn't send it", fireId: message.fireId)
            show("Couldn't send — \(message.macro.name)")
        default:
            show("Not sent")
        }
    }

    // MARK: Scheduling

    /// Logs what came due while closed and plans the next notifications. Runs on open and after changes.
    func refreshSchedule() async {
        notificationStatus = await Scheduler.authorization()
        let needs = store.macros.contains { $0.enabled && $0.triggerType == .scheduled }
        if needs && notificationStatus == .notDetermined {
            await Scheduler.requestPermission()
            notificationStatus = await Scheduler.authorization()
        }
        await Scheduler.catchUp(store)
        await Scheduler.replan(store)
    }

    /// A tap on a send notification: Send opens the finished message, Skip logs it as skipped.
    func handle(_ tap: NotificationTap) {
        guard let id = tap.macroId, let macro = store.macro(id: id) else { return }
        let at = tap.at ?? Date()
        let fireId = tap.fireId
        let text = expandTemplate(macro.messageBody, now: at, macroName: macro.name, locale: Locale(identifier: "en_GB"))
        switch tap.action {
        case Scheduler.skipAction:
            store.record(macro, status: .failed, text: text, detail: "Skipped", at: at, fireId: fireId)
        case UNNotificationDismissActionIdentifier:
            break
        default:
            tab = .macros
            switch macro.actionType {
            case .sms:
                compose(macro, at: at, fireId: fireId)
            case .webhook: Task { await callWebhook(macro) }
            default: break
            }
        }
    }

    func openSystemSettings() {
        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
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
