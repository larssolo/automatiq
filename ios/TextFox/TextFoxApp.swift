import SwiftUI
import TextFoxCore

@main
struct TextFoxApp: App {
    @State private var app = AppState(store: MacroStore())

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .environment(app.store)
                .preferredColorScheme(.dark)
                .tint(Brand.green)
        }
    }
}
