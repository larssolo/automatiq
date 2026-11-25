import SwiftUI
import TextFoxCore

/// The trial / unlock sheet: where the user stands, the perks, and the one-time purchase.
struct UnlockSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showCode = false
    @State private var code = ""
    @State private var codeError = false
    private var p: Purchases { Purchases.shared }

    var body: some View {
        let (title, text) = copy
        VStack(spacing: 18) {
            FoxLogo().fill(Brand.foxGreen, style: FillStyle(eoFill: true)).frame(width: 64, height: 64).padding(.top, 28)
            Text(title).font(.sans(24, medium: true)).foregroundStyle(Brand.onSurface).multilineTextAlignment(.center)
            Text(text).font(.sans(15)).foregroundStyle(Brand.mute).multilineTextAlignment(.center)
            VStack(alignment: .leading, spacing: 10) {
                perk("One-time purchase. No subscription.")
                perk("Every feature and every future update.")
                perk("AI runs on your own free Gemini key, so there are no AI fees.")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(LeafShape().fill(Color(hex: 0x232325)))

            switch p.access {
            case .notStarted:
                primary(p.busy ? "Starting…" : "Start 30-day free trial") { Task { await p.startTrial() } }
                Text("Free for 30 days, then \(p.products[ProductID.unlock]?.displayPrice ?? "7 USD") once to keep it. Nothing is charged automatically.")
                    .font(.sans(12)).foregroundStyle(Brand.mute).multilineTextAlignment(.center)
            case .license(.unlocked):
                primary("Done") { dismiss() }
            case .license:
                primary(p.busy ? "Unlocking…" : p.unlockLabel) { Task { await p.unlock() } }
                if p.earlyBird { Text("Early-bird code applied.").font(.sans(13)).foregroundStyle(Brand.green) }
                else if showCode {
                    HStack {
                        TextField("", text: $code, prompt: Text("Code").foregroundStyle(Brand.mute.opacity(0.6)))
                            .textInputAutocapitalization(.characters).autocorrectionDisabled()
                            .padding(10).background(RoundedRectangle(cornerRadius: 8).stroke(Brand.hairline))
                        Button("Apply") { codeError = !p.apply(code: code) }.foregroundStyle(Brand.green)
                    }
                    if codeError { Text("That code isn't valid.").font(.sans(13)).foregroundStyle(Brand.red) }
                } else {
                    Button("Have a code?") { showCode = true }.font(.sans(14)).foregroundStyle(Brand.mute)
                }
            }
            if let m = p.message { Text(m).font(.sans(13)).foregroundStyle(Brand.amber).multilineTextAlignment(.center) }
            HStack(spacing: 24) {
                Button("Restore purchase") { Task { await p.restore() } }
                Button("Close") { dismiss() }.foregroundStyle(Brand.green)
            }
            .font(.sans(14)).foregroundStyle(Brand.mute)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .background(Brand.sheet)
        .presentationDetents([.large])
        .task { await p.load() }
    }

    private var copy: (String, String) {
        switch p.access {
        case .notStarted:
            ("Try TextFox free for 30 days", "Every feature works during the trial. Afterwards, unlock TextFox once to keep your macros running.")
        case .license(.expired):
            ("Your macros are paused", "The \(trialDays)-day free trial has ended. Your macros, folders and log are all still here. Unlock TextFox and they start running again.")
        case .license(.trial(let d)):
            (d == 0 ? "Last day of your free trial" : "\(d) \(d == 1 ? "day" : "days") left of your free trial",
             "Everything works during the trial. Unlock TextFox once to keep your macros running after it.")
        case .license(.unlocked):
            ("TextFox is unlocked", "Thank you for supporting TextFox. Every feature and future update is yours.")
        }
    }

    private func perk(_ s: String) -> some View {
        Label { Text(s).font(.sans(14)).foregroundStyle(Brand.onSurface) } icon: {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.green)
        }
    }

    private func primary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.sans(17, medium: true)).foregroundStyle(.black)
                .frame(maxWidth: .infinity, minHeight: 54).background(Capsule().fill(Brand.green))
        }
        .disabled(p.busy)
    }
}

/// The line at the top of the list: start the trial, days left (from 5), or paused.
struct LicenseBanner: View {
    let onTap: () -> Void
    private var p: Purchases { Purchases.shared }

    var body: some View {
        switch p.access {
        case .notStarted:
            banner("Start your free 30-day trial to run your macros.", Brand.green, "Start")
        case .license(.expired):
            banner("Free trial ended. Your macros are paused until you unlock TextFox.", Brand.red, "Unlock")
        case .license(.trial(let d)) where d <= trialWarnDays:
            banner(d == 0 ? "Last day of your free trial." : "\(d) \(d == 1 ? "day" : "days") left of your free trial.", Brand.amber, "Unlock")
        default:
            EmptyView()
        }
    }

    private func banner(_ text: String, _ color: Color, _ action: String) -> some View {
        HStack(spacing: 12) {
            Text(text).font(.sans(14)).foregroundStyle(Brand.onSurface)
            Spacer(minLength: 0)
            Button(action, action: onTap)
                .font(.sans(14, medium: true)).foregroundStyle(.black)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(color))
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(color.opacity(0.13)))
        .padding(.horizontal, 10)
        .padding(.top, 6)
    }
}
