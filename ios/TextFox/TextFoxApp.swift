import SwiftUI
import TextFoxCore

@main
struct TextFoxApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup { AppRoot() }
            .backgroundTask(.appRefresh(Scheduler.refreshTask)) {
                await MainActor.run { Scheduler.scheduleRefresh() }
                await Scheduler.replan(MacroStore.shared)
            }
    }
}

/// The window's content: the tabs, the fox intro on every cold start, and the first-launch guide.
/// (Debug: `-skipIntro YES` skips the intro and the guide, for the UI tests.)
struct AppRoot: View {
    @State private var app = AppState.shared
    @State private var intro = !UserDefaults.standard.bool(forKey: "skipIntro")
    @State private var onboarding = false
    @Environment(\.scenePhase) private var phase

    var body: some View {
        ZStack {
            RootView()
                .fullScreenCover(isPresented: $onboarding, onDismiss: { app.store.settings.onboarded = true }) {
                    OnboardingView { onboarding = false }
                }
            if intro {
                IntroView {
                    intro = false
                    onboarding = !app.store.settings.onboarded && !UserDefaults.standard.bool(forKey: "skipIntro")
                }
                .zIndex(1)
            }
        }
        .environment(app)
        .environment(app.store)
        .preferredColorScheme(.dark)
        .tint(Brand.green)
        .onAppear {
            AppDelegate.handler = { app.handle($0) }
            if !intro { onboarding = !app.store.settings.onboarded && !UserDefaults.standard.bool(forKey: "skipIntro") }
        }
        // Every change to the macros re-plans the notifications.
        .onChange(of: app.store.macros) { Task { await app.refreshSchedule() } }
        // So does starting the trial or unlocking (nothing is planned before).
        .onChange(of: Purchases.shared.access) { Task { await app.refreshSchedule() } }
        // "Replay" in Settings clears the flag.
        .onChange(of: app.store.settings.onboarded) { _, done in if !done { onboarding = true } }
        .onChange(of: phase) { _, now in if now == .active { Task { await app.refreshSchedule() } } }
    }
}
