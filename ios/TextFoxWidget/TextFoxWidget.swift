import AppIntents
import SwiftUI
import TextFoxCore
import WidgetKit

/// The widget's settings: which macro the small widget runs (the medium one shows the first four).
struct SelectMacroIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Choose Macro"
    static let description = IntentDescription("The macro this widget sends with one tap.")

    @Parameter(title: "Macro") var macro: MacroEntity?
}

struct MacroWidgetProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> MacroWidgetEntry {
        MacroWidgetEntry(date: .now, macros: [MacroWidgetEntry.sample])
    }

    func snapshot(for configuration: SelectMacroIntent, in context: Context) async -> MacroWidgetEntry {
        entry(for: configuration, family: context.family, now: .now)
    }

    func timeline(for configuration: SelectMacroIntent, in context: Context) async -> Timeline<MacroWidgetEntry> {
        // "Next 08:00" changes after each send; refresh at the next one (the app also reloads on every change).
        let e = entry(for: configuration, family: context.family, now: .now)
        let next = e.macros.compactMap { $0.nextFire() }.min() ?? Date().addingTimeInterval(3600)
        return Timeline(entries: [e], policy: .after(next.addingTimeInterval(60)))
    }

    private func entry(for configuration: SelectMacroIntent, family: WidgetFamily, now: Date) -> MacroWidgetEntry {
        let all = StoredMacros.load().ordered
        if family == .systemSmall {
            let chosen = configuration.macro.flatMap { c in all.first { $0.id == c.id } }
            let fallback = all.first { $0.enabled && $0.triggerType == .manual } ?? all.first { $0.triggerType != .incoming }
            return MacroWidgetEntry(date: now, macros: [chosen ?? fallback].compactMap { $0 })
        }
        // Auto-replies answer incoming texts, so there's nothing to tap-send.
        return MacroWidgetEntry(date: now, macros: Array(all.filter { $0.enabled && $0.triggerType != .incoming }.prefix(4)))
    }

}

struct TextFoxWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "TextFoxWidget", intent: SelectMacroIntent.self, provider: MacroWidgetProvider()) { entry in
            MacroWidgetView(entry: entry)
        }
        .configurationDisplayName("TextFox")
        .description("Send a macro with one tap.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct TextFoxWidgetBundle: WidgetBundle {
    var body: some Widget { TextFoxWidget() }
}
