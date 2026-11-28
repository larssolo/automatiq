import BackgroundTasks
import CoreLocation
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

    private static let locationManager = CLLocationManager()

    /// Place notifications need location access; asked when the user saves a location macro.
    static func askForLocationIfNeeded() {
        if locationManager.authorizationStatus == .notDetermined { locationManager.requestWhenInUseAuthorization() }
    }

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
        // Nothing is planned while the trial isn't running, nor after it ends.
        let access = Purchases.shared.access
        let end = Purchases.shared.trialEnd ?? .distantFuture
        // Location macros: iOS itself watches the place and shows the notification on arrival or departure.
        let places = access.canRun ? Array(store.macros.filter { $0.enabled && $0.triggerType == .location && $0.latitude != nil && $0.longitude != nil }.prefix(20)) : []
        // Both kinds share iOS's limit of 64 waiting notifications.
        let plan = access.canRun ? planFires(store.macros, after: now, budget: notificationBudget - places.count).filter { $0.at < end } : []
        // Sends Shortcuts already made (an automation can run a little before the time) aren't planned again.
        let sent = Set(store.log.filter { $0.status == .success }.compactMap(\.fireId))
        let fires = plan.filter { !sent.contains($0.id) }.compactMap { f in store.macro(id: f.macroId).map { (f, $0) } }
        let autoSend = store.settings.notificationAutomationAt != nil
        let wanted = Set(fires.map(\.0.id) + places.map { locationId($0, autoSend: autoSend) })
        let pendingRequests = await center.pendingNotificationRequests().filter { $0.identifier.hasPrefix("fire:") || $0.identifier.hasPrefix("loc:") }
        // A send whose kind of automation changed since is planned again in the new style.
        let styles = Dictionary(fires.map { ($0.0.id, style(for: $0.1, store: store).rawValue) }, uniquingKeysWith: { a, _ in a })
        let stale = pendingRequests.filter { r in
            !wanted.contains(r.identifier) || (r.identifier.hasPrefix("fire:") && (r.content.userInfo["style"] as? String ?? FireStyle.plain.rawValue) != styles[r.identifier])
        }.map(\.identifier)
        center.removePendingNotificationRequests(withIdentifiers: stale)
        let have = Set(pendingRequests.map(\.identifier)).subtracting(stale)
        for macro in places where !have.contains(locationId(macro, autoSend: autoSend)) {
            try? await center.add(locationRequest(for: macro, autoSend: autoSend))
        }
        for (fire, macro) in fires where !have.contains(fire.id) {
            try? await center.add(request(for: macro, at: fire, style: style(for: macro, store: store)))
        }
    }

    /// How a planned text's notification behaves, depending on the automation the user has.
    enum FireStyle: String {
        /// No automation: the notification asks you to tap Send.
        case plain
        /// The notification starts the "TextFox Send" shortcut (iOS 27), which sends and removes it.
        case autoSend
        /// A Time of Day automation sends it; the notification only comes 5 minutes later, if it didn't.
        case backup
    }

    static func style(for macro: Macro, store: MacroStore) -> FireStyle {
        guard macro.sendsText else { return .plain }
        if store.settings.notificationAutomationAt != nil { return .autoSend }
        if let t = macro.scheduledTime, store.settings.automatedTimes[t] != nil { return .backup }
        return .plain
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

    /// The id carries what the region depends on, so moving the pin or radius replaces it.
    static func locationId(_ m: Macro, autoSend: Bool) -> String {
        "loc:\(m.id):\(m.latitude ?? 0),\(m.longitude ?? 0),\(Int(m.radiusMeters ?? 200)),\(m.geofenceTransition ?? 1),\(stableHash(m.messageBody))" + (autoSend ? ",a" : "")
    }

    private static func locationRequest(for macro: Macro, autoSend: Bool) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = macro.name
        let sending = autoSend && macro.actionType == .sms ? "sending automatically to " : "to "
        content.subtitle = (macro.geofenceTransition == 2 ? "You left · " : "You arrived · ") + sending + macro.recipients.map(maskPhone).joined(separator: ", ")
        content.body = expandTemplate(macro.messageBody, now: Date(), macroName: macro.name, locale: Locale(identifier: "en_GB"))
        content.sound = .default
        content.categoryIdentifier = macro.actionType == .reminder ? "" : category
        content.userInfo = ["macroId": macro.id]
        let region = CLCircularRegion(center: CLLocationCoordinate2D(latitude: macro.latitude!, longitude: macro.longitude!),
                                      radius: max(macro.radiusMeters ?? 200, 100), identifier: macro.id)
        region.notifyOnEntry = macro.geofenceTransition != 2
        region.notifyOnExit = macro.geofenceTransition == 2
        return UNNotificationRequest(identifier: locationId(macro, autoSend: autoSend), content: content,
                                     trigger: UNLocationNotificationTrigger(region: region, repeats: true))
    }

    private static func request(for macro: Macro, at fire: PlannedFire, style: FireStyle = .plain) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        let text = expandTemplate(macro.messageBody, now: fire.at, macroName: macro.name, locale: Locale(identifier: "en_GB"))
        content.title = macro.name
        content.sound = .default
        content.userInfo = ["macroId": macro.id, "at": fire.at.timeIntervalSince1970, "style": style.rawValue]
        switch macro.actionType {
        case .reminder:
            content.body = text
        case .webhook:
            content.body = "Tap to run the webhook"
            content.categoryIdentifier = category
        default:
            let lead = switch style {
            case .plain: "To "
            case .autoSend: "Sending automatically to "
            case .backup: "Shortcuts didn't send it · To "
            }
            content.subtitle = lead + macro.recipients.map(maskPhone).joined(separator: ", ")
            content.body = text
            content.categoryIdentifier = category
            content.interruptionLevel = .timeSensitive
            // A number on the app icon until it's sent, also after the notification is cleared.
            if style != .autoSend { content.badge = 1 }
        }
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second],
                                                    from: fire.at.addingTimeInterval(style == .backup ? 5 * 60 : 0))
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
                    // The notification should have started the shortcut. When it plainly didn't (the
                    // shortcut was removed or its automation turned off), go back to tap-to-send.
                    if let since = store.settings.notificationAutomationAt, macro.sendsText,
                       at.timeIntervalSince1970 > since, now.timeIntervalSince(at) > 10 * 60 {
                        store.settings.notificationAutomationAt = nil
                        store.record(macro, status: .pending, text: text, detail: "Shortcuts didn't send it — tap the notification to send", at: at, fireId: fire.id)
                    } else {
                        store.record(macro, status: .pending, text: text, detail: "Waiting for you — tap the notification to send", at: at, fireId: fire.id)
                    }
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
