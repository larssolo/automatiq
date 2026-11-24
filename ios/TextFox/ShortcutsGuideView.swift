import SwiftUI
import TextFoxCore

/// How to make TextFox send on its own: iPhone apps can't send texts in the background, but a
/// Shortcuts automation can, using TextFox's actions for the text and the recipients.
struct ShortcutsGuideView: View {
    @Environment(\.dismiss) private var dismiss
    var focus: TriggerType = .scheduled

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("iPhone doesn't let apps send texts by themselves. TextFox reminds you with a notification — one tap on Send and it's gone. To send with no tap at all, let the Shortcuts app do the sending. It takes two minutes per macro.")
                        .font(.sans(15)).foregroundStyle(Brand.onSurface)

                    guide(title: "Scheduled macro", icon: "clock", steps: [
                        "Open **Shortcuts** → **Automation** → **+** → **Time of Day**.",
                        "Choose the time and the days — the same as in your macro. Pick **Run Immediately** and turn off **Notify When Run**.",
                        "Tap **New Blank Automation**, search for **TextFox** and add **Get TextFox Message**. Tap **Macro** and choose yours.",
                        "Add **Get TextFox Recipients** with the same macro.",
                        "Add **Send Message**. Set the message to **TextFox Message** and the recipients to **TextFox Recipients**. Turn off **Show When Run**.",
                        "In TextFox, switch the macro off — or keep it on to get a reminder too.",
                    ], highlight: focus == .scheduled)

                    guide(title: "Auto-reply", icon: "arrowshape.turn.up.left.fill", steps: [
                        "Open **Shortcuts** → **Automation** → **+** → **Message**.",
                        "Set **Sender** (or leave it as anyone) and, if you like, **Message Contains**. Pick **Run Immediately**.",
                        "Add **Get TextFox Auto-Reply**. Set **Sender** to **Shortcut Input › Sender** and **Message** to **Shortcut Input › Content**.",
                        "Add **If** → **TextFox Auto-Reply** **has any value**.",
                        "Inside the If, add **Send Message** with **TextFox Auto-Reply** to **Shortcut Input › Sender**. Turn off **Show When Run**.",
                    ], highlight: focus == .incoming)

                    guide(title: "Charging, Wi-Fi, Bluetooth, arriving", icon: "bolt.badge.automatic", steps: [
                        "Make a **Manual** macro in TextFox with the message.",
                        "In **Shortcuts** → **Automation** → **+**, pick **Charger**, **Wi-Fi**, **Bluetooth** or **Arrive**.",
                        "Add **Get TextFox Message**, **Get TextFox Recipients** and **Send Message** as for a scheduled macro.",
                    ], highlight: false)

                    Text("TextFox logs every message it hands to Shortcuts, so the Log shows what went out.")
                        .font(.sans(13)).foregroundStyle(Brand.mute)
                    Button {
                        if let url = URL(string: "shortcuts://") { UIApplication.shared.open(url) }
                    } label: {
                        Text("Open Shortcuts").font(.sans(16, medium: true)).foregroundStyle(.black)
                            .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(Brand.green))
                    }
                }
                .padding(18)
            }
            .background(Brand.sheet)
            .navigationTitle("Send automatically")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private func guide(title: String, icon: String, steps: [String], highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(title, systemImage: icon).font(.mono(14, medium: true)).foregroundStyle(highlight ? Brand.green : Brand.onSurface)
            ForEach(Array(steps.enumerated()), id: \.offset) { i, step in
                HStack(alignment: .top, spacing: 10) {
                    Text("\(i + 1)").font(.mono(13, medium: true)).foregroundStyle(.black)
                        .frame(width: 22, height: 22).background(Circle().fill(highlight ? Brand.green : Brand.mute))
                    Text(LocalizedStringKey(step)).font(.sans(15)).foregroundStyle(Brand.onSurface)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LeafShape().fill(Color(hex: 0x232325)))
        .overlay(LeafShape().stroke(highlight ? Brand.green.opacity(0.4) : Color.white.opacity(0.06)))
    }
}
