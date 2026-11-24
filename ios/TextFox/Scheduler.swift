import BackgroundTasks
import Foundation
import TextFoxCore
import UIKit
import UserNotifications

/// Turns the scheduled macros into local notifications. iOS doesn't let an app send a text in the
/// background, so at the send time TextFox shows a notification; tapping Send opens the finished
/// message (fully automatic sending goes through a Shortcuts automation, milestone 4).
@MainActor
enum Scheduler {
    static let category = "TEXTFOX_FIRE"
    static let sendAction = "SEND"
    static let skipAction = "SKIP"

    static let refreshTask = "app.kodekid.textfox.refresh"

    /// Asks iOS to wake the app now and then to top up the notifications (at most 64 can wait).
    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTask)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func registerCategories() {
        let send = UNNotificationAction(identifier: sendAction, title: "Send", options: [.foreground])
        let skip = UNNotificationAction(identifier: skipAction, title: "Skip", options: [.destructive])
        let fire = UNNotificationCategory(identifier: category, actions: [send, skip], intentIdentifiers: [])
        UNUserNotificationCenter.current().setNotificationCategories([fire])
    }

    static func authorization() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Asks once; afterwards the answer stands and the list shows a banner if it was no.
    @discardableResult
    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Replaces the pending TextFox notifications with the soonest planned sends.
    static func replan(_ store: MacroStore, now: Date = Date()) async {
        let center = UNUserNotificationCenter.current()
        guard await authorization() == .authorized else { return }
        scheduleRefresh()
        let plan = planFires(store.macros, after: now)
        let wanted = Set(plan.map(\.id))
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("fire:") }
        let stale = pending.filter { !wanted.contains($0) }
        center.removePendingNotificationRequests(withIdentifiers: stale)
        let have = Set(pending)
        for fire in plan where !have.contains(fire.id) {
            guard let macro = store.macro(id: fire.macroId) else { continue }
            try? await center.add(request(for: macro, at: fire))
        }
    }

    #if DEBUG
    /// A send notification for [macro] in a few seconds, for checking the whole path in the simulator.
    static func testFire(_ macro: Macro, in seconds: TimeInterval = 3) async {
        let fire = PlannedFire(macroId: macro.id, at: Date().addingTimeInterval(seconds))
        let r = request(for: macro, at: fire)
        // Its own id prefix, so re-planning (which drops unknown "fire:" ids) leaves it alone.
        let test = UNNotificationRequest(identifier: "test:" + fire.id, content: r.content,
                                         trigger: UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false))
        try? await UNUserNotificationCenter.current().add(test)
    }
    #endif

    private static func request(for macro: Macro, at fire: PlannedFire) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        let text = expandTemplate(macro.messageBody, now: fire.at, macroName: macro.name, locale: Locale(identifier: "en_GB"))
        content.title = macro.name
        content.sound = .default
        content.userInfo = ["macroId": macro.id, "at": fire.at.timeIntervalSince1970]
        switch macro.actionType {
        case .reminder:
            content.body = text
        case .webhook:
            content.body = "Tap to run the webhook"
            content.categoryIdentifier = category
        default:
            content.subtitle = "To " + macro.recipients.map(maskPhone).joined(separator: ", ")
            content.body = text
            content.categoryIdentifier = category
            content.interruptionLevel = .timeSensitive
        }
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fire.at)
        return UNNotificationRequest(identifier: fire.id, content: content,
                                     trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false))
    }

    /// Logs the sends that came due while the app was closed, then moves each macro's mark forward.
    static func catchUp(_ store: MacroStore, now: Date = Date()) async {
        let allowed = await authorization() == .authorized
        for macro in store.macros where macro.triggerType == .scheduled && macro.enabled {
            let since = Date(timeIntervalSince1970: TimeInterval(macro.lastScheduledFireAt ?? macro.createdAt) / 1000)
            let due = macro.dueFires(since: since, until: now)
            guard !due.isEmpty else { continue }
            for at in due {
                let fire = PlannedFire(macroId: macro.id, at: at)
                let text = expandTemplate(macro.messageBody, now: at, macroName: macro.name, locale: Locale(identifier: "en_GB"))
                if !allowed {
                    store.record(macro, status: .failed, text: text, detail: "Notifications are off for TextFox", at: at, fireId: fire.id)
                } else if macro.actionType == .reminder {
                    store.record(macro, status: .success, text: text, detail: "Reminder", at: at, fireId: fire.id)
                } else if !store.log.contains(where: { $0.fireId == fire.id }) {
                    store.record(macro, status: .pending, text: text, detail: "Waiting for you — tap the notification to send", at: at, fireId: fire.id)
                }
            }
            store.markScheduled(macro, at: now)
        }
    }
}

/// What a notification tap carries, copied out of the (non-Sendable) system response.
struct NotificationTap: Sendable {
    let action: String
    let fireId: String
    let macroId: String?
    let at: Date?
}

/// Receives notification taps and shows notifications while the app is open.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Set by the app once its state exists; a tap that arrives earlier waits in `pending`.
    @MainActor static var handler: ((NotificationTap) -> Void)? {
        didSet { if let handler, let p = pending { pending = nil; handler(p) } }
    }
    @MainActor private static var pending: NotificationTap?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        MainActor.assumeIsolated { Scheduler.registerCategories() }
        return true
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let tap = NotificationTap(action: response.actionIdentifier, fireId: response.notification.request.identifier,
                                  macroId: info["macroId"] as? String,
                                  at: (info["at"] as? TimeInterval).map(Date.init(timeIntervalSince1970:)))
        await MainActor.run {
            if let handler = AppDelegate.handler { handler(tap) } else { AppDelegate.pending = tap }
        }
    }
}
