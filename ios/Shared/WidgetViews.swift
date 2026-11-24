import SwiftUI
import TextFoxCore
import WidgetKit

// The widget's look, shared so the app can show the same thing (a debug preview).

struct MacroWidgetEntry: TimelineEntry {
    let date: Date
    /// The chosen macro (small) or the first macros in list order (medium).
    let macros: [Macro]

    static let sample: Macro = {
        var m = Macro(id: "sample", name: "Running late", triggerType: .manual, recipients: ["+4512345678"],
                      messageBody: "Running 10 minutes late!")
        m.cardColor = 0xFFE9_1E63
        return m
    }()
}

func runURL(_ m: Macro) -> URL { URL(string: "textfox://run/\(m.id)")! }

struct MacroWidgetView: View {
    let entry: MacroWidgetEntry
    /// Set when shown outside a widget (the app's preview), where the system gives no family.
    var familyOverride: WidgetFamily? = nil
    @Environment(\.widgetFamily) private var systemFamily
    private var family: WidgetFamily { familyOverride ?? systemFamily }

    var body: some View {
        Group {
            if entry.macros.isEmpty {
                VStack(spacing: 8) {
                    FoxLogo().fill(Brand.foxGreen, style: FillStyle(eoFill: true)).frame(width: 34, height: 34)
                    Text("Open TextFox to add a macro").font(.sans(13)).foregroundStyle(Brand.mute).multilineTextAlignment(.center)
                }
            } else if family == .systemSmall, let m = entry.macros.first {
                small(m)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        FoxLogo().fill(Brand.foxGreen, style: FillStyle(eoFill: true)).frame(width: 18, height: 18)
                        Text("textfox").font(.mono(13, medium: true)).foregroundStyle(Brand.onSurface)
                        Spacer()
                    }
                    ForEach(entry.macros, id: \.id) { m in
                        Link(destination: runURL(m)) { row(m) }
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .containerBackground(for: .widget) {
            ZStack {
                Brand.background
                LinearGradient(colors: [(entry.macros.first?.accent ?? Brand.green).opacity(0.18), .clear],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
    }

    private func small(_ m: Macro) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                ZStack {
                    Circle().fill(m.accent.opacity(0.2))
                    Image(systemName: m.cardIcon).font(.system(size: 16, weight: .semibold)).foregroundStyle(m.accent)
                }
                .frame(width: 36, height: 36)
                Spacer()
                FoxLogo().fill(Brand.foxGreen, style: FillStyle(eoFill: true)).frame(width: 20, height: 20)
            }
            Spacer()
            Text(m.name).font(.sans(16, medium: true)).foregroundStyle(Brand.onSurface).lineLimit(2)
            if let next = m.nextFire(after: entry.date) {
                Text("▸ \(formatNextFire(next, now: entry.date))").font(.mono(11)).foregroundStyle(Brand.green).lineLimit(1)
            } else {
                Text(m.compactSummary()).font(.sans(11)).foregroundStyle(Brand.mute).lineLimit(1)
            }
            HStack(spacing: 4) {
                Image(systemName: "paperplane.fill").font(.system(size: 10, weight: .bold))
                Text("Tap to send").font(.sans(11, medium: true))
            }
            .foregroundStyle(.black)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(Brand.green))
            .padding(.top, 6)
        }
        .widgetURL(runURL(m))
    }

    private func row(_ m: Macro) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2).fill(m.accent).frame(width: 3, height: 22)
            Image(systemName: m.cardIcon).font(.system(size: 12, weight: .semibold)).foregroundStyle(m.accent).frame(width: 16)
            Text(m.name).font(.sans(13, medium: true)).foregroundStyle(Brand.onSurface).lineLimit(1)
            Spacer(minLength: 4)
            Image(systemName: "paperplane.fill").font(.system(size: 11)).foregroundStyle(Brand.green)
        }
        .padding(.vertical, 3)
    }
}

