import SwiftUI
import UIKit

/// How the app looks: the background (hue, saturation, brightness of the backdrop image), how solid
/// the cards are, and the "iPhone look" — calmer colours, the system font and even corners.
/// Kept in shared defaults so the widget follows the app.
struct Look: Codable, Hashable, Sendable {
    /// Degrees, 0…360, turned on the backdrop image (blue at 0).
    var hue: Double = 150
    /// 0 = grey, 1 = the image's own colour, 2 = extra strong.
    var saturation: Double = 0.25
    /// 0.3 = much darker … 1.6 = brighter.
    var brightness: Double = 0.4
    /// 0 = see-through cards, 1 = solid.
    var cardOpacity: Double = 0.9
    var iPhoneStyle = true

    static let presets: [(name: String, look: Look)] = [
        ("Midnight", Look()),
        ("Fox", Look(hue: 279, saturation: 0.27, brightness: 1.03, cardOpacity: 0.94, iPhoneStyle: false)),
        ("Ocean", Look(hue: 0, saturation: 0.8, brightness: 0.7, cardOpacity: 0.92, iPhoneStyle: true)),
        ("Graphite", Look(hue: 0, saturation: 0, brightness: 0.55, cardOpacity: 0.9, iPhoneStyle: true)),
    ]

    private static let key = "look"
    nonisolated(unsafe) private static let defaults = UserDefaults(suiteName: "group.app.kodekid.textfox") ?? .standard

    /// The look in use. Read by colours, fonts and shapes; the app re-renders when it changes.
    nonisolated(unsafe) static var current: Look = {
        guard let d = defaults.data(forKey: key), let l = try? JSONDecoder().decode(Look.self, from: d) else { return Look() }
        return l
    }()

    static func save(_ look: Look) {
        current = look
        if let d = try? JSONEncoder().encode(look) { defaults.set(d, forKey: key) }
    }

    /// A macro or folder colour in this look: the iPhone look takes the edge off the bold Material colours.
    func accent(_ c: Color) -> Color {
        guard iPhoneStyle else { return c }
        var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(c).getHue(&h, saturation: &s, brightness: &b, alpha: &a)
        return Color(hue: h, saturation: s * 0.62, brightness: min(1, b * 0.95 + 0.04), opacity: a)
    }
}

/// The backdrop: the grainy image turned to the user's colours, with soft glows on top.
struct BackdropView: View {
    var look: Look = .current

    var body: some View {
        GeometryReader { g in
            ZStack {
                Color.black
                Image("Backdrop")
                    .resizable()
                    .scaledToFill()
                    .frame(width: g.size.width, height: g.size.height)
                    .clipped()
                    .saturation(look.saturation)
                    .hueRotation(.degrees(look.hue))
                    .brightness(look.brightness > 1 ? (look.brightness - 1) * 0.5 : 0)
                    .opacity(min(look.brightness, 1))
                RadialGradient(colors: [Brand.green.opacity(0.08), .clear], center: .init(x: 0.15, y: 0.05),
                               startRadius: 0, endRadius: g.size.height * 0.5)
            }
        }
    }
}
