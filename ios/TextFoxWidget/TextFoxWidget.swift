import SwiftUI
import WidgetKit

/// Placeholder widget so the extension target builds; becomes the one-tap macro widget in milestone 4.
struct TextFoxEntry: TimelineEntry {
    let date: Date
}

struct TextFoxProvider: TimelineProvider {
    func placeholder(in context: Context) -> TextFoxEntry { TextFoxEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (TextFoxEntry) -> Void) {
        completion(TextFoxEntry(date: .now))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<TextFoxEntry>) -> Void) {
        completion(Timeline(entries: [TextFoxEntry(date: .now)], policy: .never))
    }
}

struct TextFoxWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TextFoxWidget", provider: TextFoxProvider()) { _ in
            Text("textfox")
                .font(.headline.monospaced())
                .foregroundStyle(.green)
                .containerBackground(.black, for: .widget)
        }
        .configurationDisplayName("TextFox")
        .description("Send a macro with one tap.")
        .supportedFamilies([.systemSmall])
    }
}

@main
struct TextFoxWidgetBundle: WidgetBundle {
    var body: some Widget { TextFoxWidget() }
}
