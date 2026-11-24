import SwiftUI
import TextFoxCore

@main
struct TextFoxApp: App {
    var body: some Scene {
        WindowGroup {
            MacroListView()
                .preferredColorScheme(.dark)
        }
    }
}
