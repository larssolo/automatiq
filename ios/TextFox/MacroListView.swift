import SwiftUI
import TextFoxCore

/// Placeholder home screen: proves the app, the core package and the theme are wired together.
/// Replaced by the real list in milestone 2 (docs/ios/PLAN.md).
struct MacroListView: View {
    private let sample: [Macro] = {
        var m = Macro(name: "Good morning, Mom", triggerType: .scheduled, scheduledTime: "08:00",
                      recipients: ["+4512345678"], messageBody: "Good morning {modtager}!")
        m.daysOfWeek = [1, 2, 3, 4, 5]
        return [m]
    }()

    var body: some View {
        NavigationStack {
            List(sample) { macro in
                VStack(alignment: .leading, spacing: 6) {
                    Text(macro.name).font(.headline)
                    Text(formatRecurrence(Set(macro.daysOfWeek), weekInterval: macro.weekInterval))
                        .font(.subheadline).foregroundStyle(Brand.mute)
                    if let next = macro.nextFire() {
                        Text("Next \(next.formatted(date: .abbreviated, time: .shortened))")
                            .font(.caption.monospaced()).foregroundStyle(Brand.green)
                    }
                }
                .listRowBackground(Brand.card)
            }
            .scrollContentBackground(.hidden)
            .background(Brand.ink)
            .navigationTitle("textfox")
        }
        .tint(Brand.green)
    }
}

#Preview { MacroListView() }
