import SwiftUI
import TextFoxCore

/// The "TextFox Send" shortcut, shared from iCloud. Until it's set, the app shows how to build it.
let sendShortcutURL: URL? = nil

/// iOS 27 can start a shortcut when an app's notification arrives, and a shared shortcut brings that
/// automation along: one shortcut then sends every TextFox text, at any time. Older iPhones need a
/// Time of Day automation per send time.
let supportsNotificationAutomation = ProcessInfo.processInfo.operatingSystemVersion.majorVersion >= 27

/// Settings → Send automatically. iOS 27: get the shortcut and switch it on. Older iPhones: the
/// shortcut, then one automation per send time, ticked off as soon as each has run.
struct AutomationSection: View {
    @Environment(MacroStore.self) private var store
    @Environment(AppState.self) private var app
    @State private var sheet: AutomationSheet?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if supportsNotificationAutomation { oneSwitch } else { perTime }
            OutlineButton(title: "Auto-replies and other triggers", color: Brand.green) { sheet = .guide }
        }
        .onChange(of: app.automationSetup) { _, open in
            if open { app.automationSetup = false; startSetup() }
        }
        .onAppear {
            if app.automationSetup { app.automationSetup = false; startSetup() }
        }
        .sheet(item: $sheet) { s in
            Group {
                switch s {
                case .shortcut: AutomationStepsView(title: "TextFox Send", steps: buildSteps) { sheet = nil }
                case .time(let t): AutomationStepsView(title: "Automation at \(t)", steps: timeSteps(t)) { sheet = nil }
                case .guide: ShortcutsGuideView(focus: .incoming)
                }
            }
            .id(s.id)
        }
    }

    /// iOS 27: one shortcut that starts itself on every TextFox notification.
    @ViewBuilder private var oneSwitch: some View {
        Hint("iPhone only lets the Shortcuts app send texts by itself. Add the TextFox Send shortcut once and every TextFox text goes out on time with no tap — scheduled ones and places.")
        HStack(spacing: 10) {
            let works = store.settings.notificationAutomationAt != nil
            Image(systemName: works ? "checkmark.circle.fill" : "circle.dashed").foregroundStyle(works ? Brand.green : Brand.mute)
            Text(works ? "Working — last sent \(formatNextFire(Date(timeIntervalSince1970: store.settings.notificationAutomationAt!)))" : "Not set up yet")
                .font(.sans(15)).foregroundStyle(Brand.onSurface)
        }
        Button(action: startSetup) {
            Label(sendShortcutURL == nil ? "Set up TextFox Send" : "Add the TextFox Send shortcut", systemImage: "bolt.badge.automatic.fill")
                .font(.sans(16, medium: true)).foregroundStyle(.black)
                .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(Brand.green))
        }
        .buttonStyle(.plain)
        if sendShortcutURL != nil {
            Hint("In Shortcuts, tap **Add Shortcut**. Then open **TextFox Send** and turn on **Automation** at the top — shared shortcuts arrive with it switched off.")
        }
        Hint("The notification still comes; if Shortcuts can't send, tap it and send as usual.")
    }

    /// Before iOS 27: one Time of Day automation per send time.
    @ViewBuilder private var perTime: some View {
        let times = automationTimes(store.macros)
        Hint("iPhone only lets the Shortcuts app send texts by itself. Set it up once and your scheduled texts go out with no tap — the notification then only comes if Shortcuts didn't send. (On iOS 27 this is a single switch.)")

        step(1, done: !store.settings.automatedTimes.isEmpty, title: "Get the TextFox Send shortcut",
             detail: "It sends every TextFox text that's due when it runs.") {
            startSetup()
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
    }

    private func startSetup() {
        if let url = sendShortcutURL { UIApplication.shared.open(url) } else { sheet = .shortcut }
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
        // The iOS 27 editor: triggers are the first block of the shortcut; new actions land at the
        // end, so the ones for each macro are dragged inside the repeat.
        let start = supportsNotificationAutomation ? [
            "Open **Shortcuts**, tap **+** and then **Editor**. Tap the name at the top and call it **TextFox Send**.",
            "In the actions, open **Automation** → **Notification**. Tap **App** and choose **TextFox**. Leave **Automation** on and **Notify** off.",
        ] : [
            "Open **Shortcuts**, tap **+** at the top and name the shortcut **TextFox Send**.",
        ]
        return start + [
            "Search for **Get Due TextFox Macros** and add it, then add **Repeat with Each**.",
            "Add **Get TextFox Message**. Hold it and drag it up between **Repeat** and **End Repeat** — it switches to **Repeat Item**.",
            "Add **Get TextFox Recipients** and drag it inside the repeat too. If **Macro** is empty, tap it and choose **Repeat Item**.",
            "Add **Send Message** and drag it inside, last. Tap **Message** → **TextFox Message**, and **Recipients** → **Recipients**.",
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
    let title: String
    let steps: [String]
    let close: () -> Void

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
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done", action: close) } }
        }
    }
}
