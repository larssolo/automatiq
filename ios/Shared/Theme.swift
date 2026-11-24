import SwiftUI

/// TextFox's colours, the same as the Android app and the website.
enum Brand {
    /// TextFox green; the iPhone look uses Apple's own dark-mode green.
    static var green: Color { Look.current.iPhoneStyle ? Color(hex: 0x30D158) : Color(hex: 0x00E676) }
    /// The fox logo's green, a shade darker than `green`.
    static var foxGreen: Color { Look.current.iPhoneStyle ? Color(hex: 0x28B84C) : Color(hex: 0x00C853) }
    static let ink = Color(hex: 0x090B0A)
    static let background = Color(hex: 0x0D0D0D)
    static var surface: Color { Look.current.iPhoneStyle ? Color(hex: 0x1C1C1E) : Color(hex: 0x1A1A1A) }
    static let sheet = Color(hex: 0x1C1C1E)
    static let card = Color(hex: 0x151A17)
    static let onSurface = Color(hex: 0xF0F0F0)
    static let mute = Color(hex: 0xAAAAAA)
    static var red: Color { Look.current.iPhoneStyle ? Color(hex: 0xFF453A) : Color(hex: 0xFF5252) }
    static var amber: Color { Look.current.iPhoneStyle ? Color(hex: 0xFF9F0A) : Color(hex: 0xFFB300) }
    static let hairline = Color.white.opacity(0.15)
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: opacity)
    }

    /// An Android ARGB colour as stored in macros and backups.
    init(argb: Int64) { self.init(hex: UInt32(truncatingIfNeeded: argb & 0xFFFFFF)) }
}

/// DM Sans for text and JetBrains Mono for the terminal accents, as on Android; the iPhone look uses
/// Apple's system font (SF Pro, SF Mono), which also follows the user's text size.
extension Font {
    static func sans(_ size: CGFloat, medium: Bool = false) -> Font {
        Look.current.iPhoneStyle ? .system(size: size + 0.5, weight: medium ? .semibold : .regular)
            : .custom(medium ? "DMSans-Medium" : "DMSans-Regular", size: size)
    }
    static func mono(_ size: CGFloat, medium: Bool = false) -> Font {
        Look.current.iPhoneStyle ? .system(size: size, weight: medium ? .semibold : .regular, design: .monospaced)
            : .custom(medium ? "JetBrainsMono-Medium" : "JetBrainsMono-Regular", size: size)
    }
}

/// The asymmetric "leaf-cut" corners: TextFox's signature card shape. The iPhone look uses even,
/// continuous corners like the system's grouped lists.
struct LeafShape: Shape {
    var big: CGFloat = 18, small: CGFloat = 6
    func path(in r: CGRect) -> Path {
        if Look.current.iPhoneStyle { return Path(roundedRect: r, cornerRadius: 16, style: .continuous) }
        return Path(roundedRect: r, cornerRadii: .init(topLeading: big, bottomLeading: small, bottomTrailing: big, topTrailing: small))
    }
}

/// The add button's blob cut: the leaf corners grown up to button size.
struct BlobShape: Shape {
    func path(in r: CGRect) -> Path {
        Path(roundedRect: r, cornerRadii: .init(topLeading: 26, bottomLeading: 14, bottomTrailing: 26, topTrailing: 14))
    }
}

/// A physical folder: a rounded tab raised over the top left, then the leaf corners on the body.
struct FolderTabShape: Shape {
    static let tabWidth: CGFloat = 0.38, tabHeight: CGFloat = 12
    func path(in r: CGRect) -> Path {
        let tabW = r.width * Self.tabWidth, tabH = Self.tabHeight
        let slant: CGFloat = 16, rTab: CGFloat = 8, rSmall: CGFloat = 6, rBig: CGFloat = 18
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rTab))
        p.addQuadCurve(to: CGPoint(x: rTab, y: 0), control: .zero)
        p.addLine(to: CGPoint(x: tabW - slant, y: 0))
        p.addLine(to: CGPoint(x: tabW, y: tabH))
        p.addLine(to: CGPoint(x: r.width - rSmall, y: tabH))
        p.addQuadCurve(to: CGPoint(x: r.width, y: tabH + rSmall), control: CGPoint(x: r.width, y: tabH))
        p.addLine(to: CGPoint(x: r.width, y: r.height - rBig))
        p.addQuadCurve(to: CGPoint(x: r.width - rBig, y: r.height), control: CGPoint(x: r.width, y: r.height))
        p.addLine(to: CGPoint(x: rSmall, y: r.height))
        p.addQuadCurve(to: CGPoint(x: 0, y: r.height - rSmall), control: CGPoint(x: 0, y: r.height))
        p.closeSubpath()
        return p
    }
}

/// The TextFox fox head with the three dots cut out (the Android status-bar icon, 24×24 viewport).
struct FoxLogo: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 3.25, y: 3.32))
        p.addLine(to: CGPoint(x: 9.01, y: 7.66))
        p.addLine(to: CGPoint(x: 14.99, y: 7.66))
        p.addLine(to: CGPoint(x: 20.75, y: 3.32))
        p.addLine(to: CGPoint(x: 21.13, y: 10.06))
        p.addLine(to: CGPoint(x: 22.25, y: 12.75))
        p.addLine(to: CGPoint(x: 13.8, y: 20.31))
        p.addQuadCurve(to: CGPoint(x: 10.2, y: 20.31), control: CGPoint(x: 12.0, y: 21.73))
        p.addLine(to: CGPoint(x: 1.75, y: 12.75))
        p.addLine(to: CGPoint(x: 2.87, y: 10.06))
        p.closeSubpath()
        for x in [8.78, 12.0, 15.22] {
            p.addEllipse(in: CGRect(x: x - 1.27, y: 12.0 - 1.27, width: 2.54, height: 2.54))
        }
        let s = min(rect.width, rect.height) / 24
        return p.applying(CGAffineTransform(translationX: rect.minX + (rect.width - 24 * s) / 2,
                                            y: rect.minY + (rect.height - 24 * s) / 2).scaledBy(x: s, y: s))
    }
}

/// The green/amber pill switch used across the app (green on, amber off), like Android's ThemedSwitch.
struct ThemedToggle: View {
    @Binding var isOn: Bool
    var width: CGFloat = 46, height: CGFloat = 26

    var body: some View {
        let knob = height - 6
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule().fill(isOn ? Brand.green : Brand.amber)
            Circle().fill(.white).frame(width: knob, height: knob).padding(.horizontal, 3)
                .shadow(color: .black.opacity(0.25), radius: 1, y: 1)
        }
        .frame(width: width, height: height)
        .contentShape(Capsule())
        .onTapGesture {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) { isOn.toggle() }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(isOn ? "On" : "Off")
        .accessibilityAction { isOn.toggle() }
    }
}

/// The slow breath of an armed card's edge, shared by every card.
struct BreathingVein: View {
    let color: Color
    let alive: Bool
    @State private var phase = false

    var body: some View {
        Rectangle()
            .fill(color.opacity(alive ? (phase ? 1 : 0.55) : 0.35))
            .frame(width: 4)
            .onAppear {
                guard alive else { return }
                withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) { phase = true }
            }
    }
}
