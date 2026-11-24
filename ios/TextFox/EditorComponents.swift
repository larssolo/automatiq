import CoreLocation
import SwiftUI
import TextFoxCore

/// A card in the editor with a mono, amber-ish header like "▸ WHEN".
struct EditorSection<Trailing: View, Content: View>: View {
    let icon: String
    let title: String
    let accent: Color
    @ViewBuilder var trailing: Trailing
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.mono(13, medium: true)).tracking(2)
                Spacer()
                trailing
            }
            .foregroundStyle(accent)
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LeafShape().fill(Color(hex: 0x232325)))
        .overlay(LeafShape().stroke(Color.white.opacity(0.06)))
    }
}

extension EditorSection where Trailing == EmptyView {
    init(icon: String, title: String, accent: Color, @ViewBuilder content: () -> Content) {
        self.init(icon: icon, title: title, accent: accent, trailing: { EmptyView() }, content: content)
    }
}

/// Square tiles for picking the trigger or the action, lit in the macro's colour.
struct ChoiceTiles<T: Hashable>: View {
    let options: [(value: T, icon: String, label: String)]
    @Binding var selected: T
    let accent: Color
    var columns = 4

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: columns), spacing: 8) {
            ForEach(options, id: \.value) { o in
                let on = o.value == selected
                Button {
                    selected = o.value
                    UISelectionFeedbackGenerator().selectionChanged()
                } label: {
                    VStack(spacing: 5) {
                        Image(systemName: o.icon).font(.system(size: 18))
                        Text(o.label).font(.sans(12, medium: on)).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .foregroundStyle(on ? .white : Brand.mute)
                    .frame(maxWidth: .infinity, minHeight: 62)
                    .background(RoundedRectangle(cornerRadius: 12).fill(on ? accent.opacity(0.22) : Color.white.opacity(0.05)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? accent : Color.white.opacity(0.1), lineWidth: on ? 1.5 : 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Two or more joined buttons (Repeat | Once, Ask me first | Send automatically).
struct Segmented<T: Hashable>: View {
    let options: [(value: T, label: String)]
    @Binding var selected: T
    let accent: Color

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { i, o in
                let on = o.value == selected
                Button { selected = o.value } label: {
                    Text(o.label)
                        .font(.sans(14, medium: on))
                        .foregroundStyle(on ? .white : Brand.mute)
                        .lineLimit(1).minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(on ? accent.opacity(0.22) : .clear)
                }
                .buttonStyle(.plain)
                if i < options.count - 1 { Rectangle().fill(Brand.hairline).frame(width: 1) }
            }
        }
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Brand.hairline))
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// The seven weekday circles: green on, red off.
struct DayCircles: View {
    @Binding var days: Set<Int>
    private let labels = ["M", "T", "W", "T", "F", "S", "S"]

    var body: some View {
        HStack {
            ForEach(1...7, id: \.self) { d in
                let on = days.contains(d)
                Button {
                    if on { days.remove(d) } else { days.insert(d) }
                    UISelectionFeedbackGenerator().selectionChanged()
                } label: {
                    Text(labels[d - 1]).font(.sans(15, medium: true))
                        .foregroundStyle(on ? .black : .white)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(on ? Brand.green : Brand.red.opacity(0.85)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Calendar.current.weekdaySymbols[d % 7])
                .accessibilityValue(on ? "On" : "Off")
                if d < 7 { Spacer(minLength: 0) }
            }
        }
    }
}

/// A row with a title, a grey explanation and the themed switch.
struct SwitchRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.sans(16)).foregroundStyle(Brand.onSurface)
                if let subtitle { Text(subtitle).font(.sans(12)).foregroundStyle(Brand.mute) }
            }
            Spacer()
            ThemedToggle(isOn: $isOn)
        }
    }
}

/// A grey explanation line.
struct Hint: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.sans(13)).foregroundStyle(Brand.mute).fixedSize(horizontal: false, vertical: true) }
}

/// An outlined text field with a small label above, like the Android form fields.
struct FieldBox<Content: View>: View {
    let label: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.sans(12)).foregroundStyle(Brand.mute)
            content
                .font(.sans(16))
                .foregroundStyle(Brand.onSurface)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.18)))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.white.opacity(0.35)))
        }
    }
}

/// A full-width outlined button ("Set end date (optional)").
struct OutlineButton: View {
    let title: String
    var color: Color = Brand.onSurface
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.sans(15, medium: true)).foregroundStyle(color)
                .frame(maxWidth: .infinity, minHeight: 44)
                .overlay(Capsule().stroke(Brand.hairline))
        }
        .buttonStyle(.plain)
    }
}

/// Asks for the current position once, for location macros.
@MainActor
final class LocationFetcher: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocationCoordinate2D?, Never>?

    func current() async -> CLLocationCoordinate2D? {
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        return await withCheckedContinuation { c in
            continuation = c
            manager.requestLocation()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let c = locations.last?.coordinate
        Task { @MainActor in self.finish(c) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }

    private func finish(_ c: CLLocationCoordinate2D?) {
        continuation?.resume(returning: c)
        continuation = nil
    }
}
