import SwiftUI
import TextFoxCore

/// The "TextFox Send" shortcut, shared from iCloud. Until it's set, the app shows how to build it.
let sendShortcutURL: URL? = nil

/// Settings → Send automatically: the shortcut once, then one automation per send time, ticked off
/// as soon as each has run.
struct AutomationSection: View {
    @Environment(MacroStore.self) private var store
    @State private var sheet: AutomationSheet?

    var body: some View {
        let times = automationTimes(store.macros)
        VStack(alignment: .leading, spacing: 14) {
            Hint("iPhone only lets the Shortcuts app send texts by itself. Set it up once and your scheduled texts go out with no tap — the notification then only comes if Shortcuts didn't send.")

            step(1, done: !store.settings.automatedTimes.isEmpty, title: "Get the TextFox Send shortcut",
                 detail: "It sends every TextFox text that's due when it runs.") {
                if let url = sendShortcutURL { UIApplication.shared.open(url) } else { sheet = .shortcut }
            } label: { Text(sendShortcutURL == nil ? "How" : "Get") }

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    number(2, done: !times.isEmpty && times.allSatisfy { store.settings.automatedTimes[$0] != nil })
                    Text("One automation per time").font(.sans(15, medium: true)).foregroundStyle(Brand.onSurface)
                }
                if times.isEmpty {
                    Hint("Make a scheduled text macro first — its time shows up here.")
                } else {
                    ForEach(times, id: \.self) { t in timeRow(t) }
                    Hint("All macros at the same time share one automation. Days, every-other-week and end dates are handled by TextFox.")
                }
            }

            OutlineButton(title: "Auto-replies and other triggers", color: Brand.green) { sheet = .guide }
        }
        .sheet(item: $sheet) { s in
            switch s {
            case .shortcut: AutomationStepsView(title: "TextFox Send", steps: buildSteps)
            case .time(let t): AutomationStepsView(title: "Automation at \(t)", steps: timeSteps(t))
            case .guide: ShortcutsGuideView(focus: .incoming)
            }
        }
    }

    private func timeRow(_ t: String) -> some View {
        HStack(spacing: 10) {
            Text(t).font(.mono(15, medium: true)).foregroundStyle(Brand.onSurface)
            let names = store.macros.filter { $0.sendsText && $0.scheduledTime == t }.map(\.name)
            Text(names.joined(separator: ", ")).font(.sans(13)).foregroundStyle(Brand.mute).lineLimit(1)
            Spacer()
            if let last = store.settings.automatedTimes[t] {
                Label("Ran \(formatNextFire(Date(timeIntervalSince1970: last)))", systemImage: "checkmark.circle.fill")
                    .font(.sans(13)).foregroundStyle(Brand.green).labelStyle(.titleAndIcon)
            } else {
                Button("Add") { sheet = .time(t) }.font(.sans(14, medium: true)).foregroundStyle(Brand.green)
            }
        }
        .padding(.leading, 32)
    }

    private func step(_ n: Int, done: Bool, title: String, detail: String, action: @escaping () -> Void,
                      @ViewBuilder label: () -> some View) -> some View {
        HStack(alignment: .top, spacing: 10) {
            number(n, done: done)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.sans(15, medium: true)).foregroundStyle(Brand.onSurface)
                Text(detail).font(.sans(13)).foregroundStyle(Brand.mute)
            }
            Spacer()
            Button(action: action, label: label).font(.sans(14, medium: true)).foregroundStyle(Brand.green)
        }
    }

    private func number(_ n: Int, done: Bool) -> some View {
        ZStack {
            Circle().fill(done ? Brand.green : Brand.mute)
            if done { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)) }
            else { Text("\(n)").font(.mono(12, medium: true)) }
        }
        .foregroundStyle(.black)
        .frame(width: 22, height: 22)
    }

    private var buildSteps: [String] {
        [
            "Open **Shortcuts**, tap **+** at the top and name the shortcut **TextFox Send**.",
            "Search for **TextFox** and add **Get Due TextFox Macros**.",
            "Search for **Repeat with Each** and add it. It repeats with the due macros by itself.",
            "Inside the repeat, add **Get TextFox Message**. Tap **Macro** → **Select Variable** → **Repeat Item**.",
            "Below it, add **Get TextFox Recipients** and set **Macro** to **Repeat Item** the same way.",
            "Add **Send Message**. Tap **Message** and pick **TextFox Message**; tap **Recipients** and pick **Recipients**.",
            "Tap the arrow on Send Message and turn off **Show When Run**. Tap **Done**.",
        ]
    }

    private func timeSteps(_ t: String) -> [String] {
        [
            "Open **Shortcuts** → **Automation** → **+** → **Time of Day**.",
            "Set the time to **\(t)** and **Daily**. Choose **Run Immediately** and tap **Next**.",
            "Under **My Shortcuts**, pick **TextFox Send**. That's it.",
            "The next time it runs, \(t) gets a green tick here.",
        ]
    }
}

enum AutomationSheet: Identifiable {
    case shortcut, time(String), guide
    var id: String {
        switch self {
        case .shortcut: "shortcut"
        case .time(let t): "time-\(t)"
        case .guide: "guide"
        }
    }
}

/// Numbered steps with a button that opens Shortcuts.
struct AutomationStepsView: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let steps: [String]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(Array(steps.enumerated()), id: \.offset) { i, step in
                        HStack(alignment: .top, spacing: 10) {
                            Text("\(i + 1)").font(.mono(13, medium: true)).foregroundStyle(.black)
                                .frame(width: 22, height: 22).background(Circle().fill(Brand.green))
                            Text(LocalizedStringKey(step)).font(.sans(15)).foregroundStyle(Brand.onSurface)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Button {
                        if let url = URL(string: "shortcuts://") { UIApplication.shared.open(url) }
                    } label: {
                        Text("Open Shortcuts").font(.sans(16, medium: true)).foregroundStyle(.black)
                            .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(Brand.green))
                    }
                    .padding(.top, 6)
                }
                .padding(18)
            }
            .background(Brand.sheet)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}
