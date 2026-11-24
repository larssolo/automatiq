import SwiftUI
import TextFoxCore

@main
struct TextFoxApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var app = AppState(store: MacroStore())
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .environment(app.store)
                .preferredColorScheme(.dark)
                .tint(Brand.green)
                .onAppear { AppDelegate.handler = { [app] in app.handle($0) } }
                // Every change to the macros re-plans the notifications.
                .onChange(of: app.store.macros) { Task { await app.refreshSchedule() } }
        }
        .onChange(of: phase) { _, now in
            if now == .active { Task { await app.refreshSchedule() } }
        }
        .backgroundTask(.appRefresh(Scheduler.refreshTask)) {
            await MainActor.run { Scheduler.scheduleRefresh() }
            let store = await MacroStore()
            await Scheduler.replan(store)
        }
    }
}
