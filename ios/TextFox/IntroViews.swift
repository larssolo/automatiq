import SwiftUI
import TextFoxCore

/// The fox head from the app icon (108-unit viewport), without the dots: they're drawn on top so they can move.
private struct FoxHead: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 30.09, y: 29.66))
        for (x, y) in [(45.82, 41.51), (62.18, 41.51), (77.91, 29.66), (78.93, 48.05), (82.0, 55.41), (58.91, 76.05)] {
            p.addLine(to: CGPoint(x: x, y: y))
        }
        p.addQuadCurve(to: CGPoint(x: 49.09, y: 76.05), control: CGPoint(x: 54.0, y: 79.93))
        p.addLine(to: CGPoint(x: 26.0, y: 55.41))
        p.addLine(to: CGPoint(x: 29.07, y: 48.05))
        p.closeSubpath()
        let s = min(rect.width, rect.height) / 108
        return p.applying(CGAffineTransform(scaleX: s, y: s))
    }
}

/// The green badge with the fox; its three dots hop like a "typing" bubble as [bounce] runs 0 → 1.
struct FoxBadge: View, @preconcurrency Animatable {
    var bounce: Double
    var size: CGFloat = 160

    var animatableData: Double {
        get { bounce }
        set { bounce = newValue }
    }

    var body: some View {
        let canvas = size * 1.5 // the 108-unit icon at this badge size (72 units ↦ the circle)
        let unit = canvas / 108
        ZStack {
            Circle().fill(Brand.foxGreen).frame(width: size, height: size)
            ZStack(alignment: .topLeading) {
                FoxHead().fill(Color(hex: 0x0B130E))
                ForEach(0..<3, id: \.self) { i in
                    // Two hops, each dot a beat behind the one before; all rest at the end.
                    let t = min(max(bounce * 2.36 - Double(i) * 0.18, 0), 2)
                    let lift = abs(sin(t * .pi)) * 3.2
                    Circle().fill(Brand.foxGreen)
                        .frame(width: 2.76 * 2 * unit, height: 2.76 * 2 * unit)
                        .position(x: [45.21, 54.0, 62.79][i] * unit, y: (53.36 - lift) * unit)
                }
            }
            .frame(width: canvas, height: canvas)
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

/// The cold-start moment: the badge, the dots hop, the badge rises and "TextFox" fades in, then the
/// layer fades out over the app. About 1.3 s; a tap skips it.
struct IntroView: View {
    let onFinished: () -> Void
    @State private var bounce = 0.0
    @State private var rise = 0.0
    @State private var word = 0.0
    @State private var layer = 1.0
    @State private var done = false

    var body: some View {
        ZStack {
            Brand.background.ignoresSafeArea()
            VStack(spacing: 28) {
                FoxBadge(bounce: bounce)
                Text("TextFox")
                    .font(.system(size: 38, weight: .semibold, design: Look.current.iPhoneStyle ? .default : .monospaced))
                    .foregroundStyle(.white)
                    .opacity(word)
                    .offset(y: (1 - word) * 16)
            }
            .offset(y: 48 * (1 - rise))
        }
        .opacity(layer)
        .contentShape(Rectangle())
        .onTapGesture { finish(fast: true) }
        .task {
            withAnimation(.linear(duration: 0.9)) { bounce = 1 }
            withAnimation(.easeOut(duration: 0.45).delay(0.15)) { rise = 1 }
            withAnimation(.easeOut(duration: 0.4).delay(0.3)) { word = 1 }
            try? await Task.sleep(for: .seconds(1.0))
            finish(fast: false)
        }
        .accessibilityLabel("TextFox")
    }

    private func finish(fast: Bool) {
        guard !done else { return }
        done = true
        withAnimation(.easeIn(duration: fast ? 0.15 : 0.3)) { layer = 0 }
        Task {
            try? await Task.sleep(for: .seconds(fast ? 0.15 : 0.3))
            onFinished()
        }
    }
}

/// First launch: what TextFox does, how sending works on iPhone, notifications and the free trial.
struct OnboardingView: View {
    let onDone: () -> Void
    @Environment(AppState.self) private var app
    @State private var page = 0
    @State private var askedNotifications = false

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                welcome.tag(0)
                howItWorks.tag(1)
                notifications.tag(2)
                trial.tag(3)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button {
                if page < 3 { withAnimation { page += 1 } } else { onDone() }
            } label: {
                Text(page < 3 ? "Next" : "Let's go").font(.sans(17, medium: true)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 54).background(Capsule().fill(Brand.green))
            }
            .padding(.horizontal, 24)
            Button("Skip") { onDone() }.font(.sans(14)).foregroundStyle(Brand.mute).padding(.vertical, 14)
                .opacity(page < 3 ? 1 : 0)
        }
        .background(BackdropView(look: app.look).ignoresSafeArea())
    }

    private func pageView<Extra: View>(icon: AnyView, title: String, text: String, @ViewBuilder extra: () -> Extra) -> some View {
        VStack(spacing: 22) {
            Spacer()
            icon
            Text(title).font(.sans(28, medium: true)).foregroundStyle(Brand.onSurface).multilineTextAlignment(.center)
            Text(text).font(.sans(16)).foregroundStyle(Brand.mute).multilineTextAlignment(.center)
            extra()
            Spacer()
            Spacer()
        }
        .padding(.horizontal, 30)
    }

    private func symbol(_ name: String) -> AnyView {
        AnyView(Image(systemName: name).font(.system(size: 56, weight: .semibold)).foregroundStyle(Brand.green)
            .frame(width: 120, height: 120).background(Circle().fill(Brand.green.opacity(0.14))))
    }

    private var welcome: some View {
        pageView(icon: AnyView(FoxBadge(bounce: 1, size: 120)), title: "Meet TextFox",
                 text: "Your texts on autopilot. Write a message once — TextFox has it ready at the right time, every time.") { EmptyView() }
    }

    private var howItWorks: some View {
        pageView(icon: symbol("bell.badge.fill"), title: "One tap to send",
                 text: "iPhone doesn't let apps send texts on their own. At the send time you get a notification with the finished message — tap Send and it's gone.") {
            Text("Want no tap at all? The Shortcuts app can send for you. Settings → Send automatically shows how.")
                .font(.sans(14)).foregroundStyle(Brand.onSurface.opacity(0.8)).multilineTextAlignment(.center)
        }
    }

    private var notifications: some View {
        pageView(icon: symbol("app.badge.fill"), title: "Allow notifications",
                 text: "They're how TextFox tells you a message is ready. Without them, scheduled macros stay silent.") {
            Button {
                Task { await Scheduler.requestPermission(); askedNotifications = true; app.notificationStatus = await Scheduler.authorization() }
            } label: {
                Label(askedNotifications ? (app.notificationStatus == .authorized ? "Notifications on" : "Turn on in Settings later") : "Allow notifications",
                      systemImage: app.notificationStatus == .authorized ? "checkmark.circle.fill" : "bell")
                    .font(.sans(16, medium: true)).foregroundStyle(Brand.green)
                    .padding(.horizontal, 20).padding(.vertical, 12)
                    .overlay(Capsule().stroke(Brand.green.opacity(0.6)))
            }
            .disabled(askedNotifications)
        }
    }

    private var trial: some View {
        pageView(icon: symbol("gift.fill"), title: "Free for 30 days",
                 text: "Every feature works in the trial. Afterwards, unlock TextFox once — no subscription.") {
            if Purchases.shared.access == .notStarted {
                Button { Task { await Purchases.shared.startTrial() } } label: {
                    Label(Purchases.shared.busy ? "Starting…" : "Start free trial", systemImage: "sparkles")
                        .font(.sans(16, medium: true)).foregroundStyle(Brand.green)
                        .padding(.horizontal, 20).padding(.vertical, 12)
                        .overlay(Capsule().stroke(Brand.green.opacity(0.6)))
                }
            } else {
                Label("Your trial is running", systemImage: "checkmark.circle.fill").font(.sans(16, medium: true)).foregroundStyle(Brand.green)
            }
        }
    }
}
