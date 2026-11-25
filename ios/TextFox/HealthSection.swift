import CoreLocation
import SwiftUI
import TextFoxCore
import UserNotifications

/// Settings → Phone check: is everything TextFox needs switched on, and what's coming next.
struct HealthSection: View {
    @Environment(MacroStore.self) private var store
    @Environment(AppState.self) private var app
    @State private var pending = 0
    @State private var location: CLAuthorizationStatus = .notDetermined

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            row("Notifications", ok: app.notificationStatus == .authorized,
                value: app.notificationStatus == .authorized ? "On" : app.notificationStatus == .denied ? "Off" : "Not asked yet",
                fix: app.notificationStatus == .authorized ? nil : { app.openSystemSettings() })
            if store.macros.contains(where: { $0.triggerType == .location }) {
                let ok = location == .authorizedWhenInUse || location == .authorizedAlways
                row("Location", ok: ok, value: ok ? "On" : location == .denied ? "Off" : "Not asked yet",
                    fix: ok ? nil : { app.openSystemSettings() })
            }
            row("Free trial / unlock", ok: Purchases.shared.access.canRun,
                value: Purchases.shared.access.canRun ? "Running" : "Paused", fix: Purchases.shared.access.canRun ? nil : { app.showUnlock = true })
            HStack {
                Text("Waiting notifications").font(.sans(15)).foregroundStyle(Brand.onSurface)
                Spacer()
                Text("\(pending) of 64").font(.mono(13)).foregroundStyle(Brand.mute)
            }
            let next = planFires(store.macros, after: Date(), budget: 3)
            if !next.isEmpty {
                Text("NEXT SENDS").font(.mono(11)).tracking(1.5).foregroundStyle(Brand.mute).padding(.top, 4)
                ForEach(next, id: \.id) { f in
                    HStack {
                        Text(store.macro(id: f.macroId)?.name ?? "—").font(.sans(15)).foregroundStyle(Brand.onSurface).lineLimit(1)
                        Spacer()
                        Text(formatNextFire(f.at)).font(.mono(13)).foregroundStyle(Brand.green)
                    }
                }
            }
        }
        .task {
            pending = await UNUserNotificationCenter.current().pendingNotificationRequests().count
            location = CLLocationManager().authorizationStatus
        }
    }

    private func row(_ title: String, ok: Bool, value: String, fix: (() -> Void)?) -> some View {
        HStack(spacing: 10) {
            Image(systemName: ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill").foregroundStyle(ok ? Brand.green : Brand.amber)
            Text(title).font(.sans(15)).foregroundStyle(Brand.onSurface)
            Spacer()
            if let fix { Button("Fix", action: fix).font(.sans(14, medium: true)).foregroundStyle(Brand.green) }
            else { Text(value).font(.mono(13)).foregroundStyle(Brand.mute) }
        }
    }
}
