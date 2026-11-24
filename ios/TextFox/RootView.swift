import SwiftUI
import TextFoxCore

enum Tab: String, CaseIterable {
    case macros = "Macros", log = "Log", settings = "Settings"

    var icon: String {
        switch self {
        case .macros: "bolt.fill"
        case .log: "clock.arrow.circlepath"
        case .settings: "slider.horizontal.3"
        }
    }
}

/// The three tabs with TextFox's own bottom bar (a green pill behind the current tab, as on Android).
struct RootView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        @Bindable var app = app
        ZStack(alignment: .bottom) {
            BackdropView().ignoresSafeArea()
            Group {
                switch app.tab {
                case .macros: MacroListView()
                case .log: LogView()
                case .settings: SettingsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 64) }

            VStack(spacing: 10) {
                if let toast = app.toast { ToastView(toast: toast).transition(.move(edge: .bottom).combined(with: .opacity)) }
                TabBar(tab: $app.tab)
            }
        }
        .onOpenURL { app.open($0) }
        #if DEBUG
        // Screenshots and checks in the simulator: launch with `-open textfox://…`.
        .task { if let s = UserDefaults.standard.string(forKey: "open"), let url = URL(string: s) { app.open(url) } }
        #endif
        #if DEBUG
        .sheet(isPresented: $app.showWidgetPreview) { WidgetPreviewSheet() }
        #endif
        .sheet(item: $app.outgoing) { message in
            MessageComposer(message: message) { result in app.finishedSending(message, result) }
                .ignoresSafeArea()
        }
    }
}

private struct TabBar: View {
    @Binding var tab: Tab

    var body: some View {
        HStack {
            ForEach(Tab.allCases, id: \.self) { t in
                Button {
                    tab = t
                    UISelectionFeedbackGenerator().selectionChanged()
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: t.icon)
                            .font(.system(size: 17, weight: .semibold))
                            .frame(width: 64, height: 30)
                            .background(Capsule().fill(tab == t ? Brand.green.opacity(0.16) : .clear))
                        Text(t.rawValue).font(.sans(12, medium: true))
                    }
                    .foregroundStyle(tab == t ? Brand.green : Brand.mute)
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 10)
        .padding(.bottom, 2)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22)
                .fill(Color(hex: 0x141614).opacity(0.97))
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

private struct ToastView: View {
    let toast: Toast
    @Environment(AppState.self) private var app

    var body: some View {
        HStack(spacing: 12) {
            Text(toast.text).font(.sans(14)).foregroundStyle(Brand.onSurface).lineLimit(2)
            Spacer(minLength: 0)
            if let label = toast.actionLabel {
                Button(label) {
                    toast.action?()
                    withAnimation { app.toast = nil }
                }
                .font(.sans(14, medium: true))
                .foregroundStyle(Brand.green)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0x2A2D2B)))
        .padding(.horizontal, 12)
        .onTapGesture { withAnimation { app.toast = nil } }
    }
}

/// The app's backdrop: a dark green-black wash with soft glows, like the Android ambient background.
struct BackdropView: View {
    var body: some View {
        ZStack {
            Brand.background
            RadialGradient(colors: [Brand.green.opacity(0.10), .clear], center: .init(x: 0.15, y: 0.05),
                           startRadius: 0, endRadius: 420)
            RadialGradient(colors: [Color(hex: 0x1DE9B6).opacity(0.06), .clear], center: .init(x: 0.95, y: 0.7),
                           startRadius: 0, endRadius: 380)
        }
    }
}

#if DEBUG
/// The widget as it renders on the home screen, at its real sizes, for checking in the simulator.
private struct WidgetPreviewSheet: View {
    @Environment(MacroStore.self) private var store

    var body: some View {
        let all = StoredMacros.load().ordered
        VStack(spacing: 24) {
            Text("Widget preview").font(.mono(14, medium: true)).foregroundStyle(Brand.mute)
            MacroWidgetView(entry: MacroWidgetEntry(date: .now, macros: [all.first { $0.triggerType == .manual } ?? all[0]]), familyOverride: .systemSmall)
                .padding(16).frame(width: 170, height: 170)
                .background(RoundedRectangle(cornerRadius: 24).fill(Brand.background))
            MacroWidgetView(entry: MacroWidgetEntry(date: .now, macros: Array(all.filter(\.enabled).prefix(4))), familyOverride: .systemMedium)
                .padding(16).frame(width: 364, height: 170)
                .background(RoundedRectangle(cornerRadius: 24).fill(Brand.background))
            Spacer()
        }
        .padding(.top, 30)
        .frame(maxWidth: .infinity)
        .background(Color(hex: 0x3A4A5A))
    }
}
#endif
