import SwiftUI
import TextFoxCore
import UniformTypeIdentifiers

/// Quiet hours, backup (the same file format as Android, so macros move between phones) and about.
struct SettingsView: View {
    @Environment(MacroStore.self) private var store
    @Environment(AppState.self) private var app
    @State private var exporting = false
    @State private var importing = false
    @State private var backup: BackupFile?
    @State private var guide = false

    var body: some View {
        @Bindable var store = store
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Settings").font(.sans(26, medium: true)).foregroundStyle(Brand.onSurface)

                group("Phone check") { HealthSection() }

                group("Send automatically") { AutomationSection() }

                group("Appearance") { AppearanceSection() }

                group("AI") { AISettingsSection() }

                group("Quiet hours") {
                    SwitchRow(title: "Pause auto-replies at night",
                              subtitle: "Auto-replies wait out the window and are sent right after it ends. Scheduled and manual sends still go through.",
                              isOn: $store.settings.quietHoursEnabled)
                    if store.settings.quietHoursEnabled {
                        HStack {
                            timeField("From", minutes: $store.settings.quietStart)
                            Spacer()
                            timeField("To", minutes: $store.settings.quietEnd)
                        }
                    }
                }

                group("Backup") {
                    Hint("One file with all macros, folders and settings. It's the same format as TextFox on Android, so you can move your macros between phones.")
                    Button {
                        do { backup = BackupFile(data: try store.backupData()); exporting = true }
                        catch { app.show("Couldn't make the backup: \(error.localizedDescription)") }
                    } label: {
                        Text("Export backup").font(.sans(16, medium: true)).foregroundStyle(.black)
                            .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(Brand.green))
                    }
                    OutlineButton(title: "Import backup", color: Brand.green) { importing = true }
                }

                group("TextFox") {
                    let access = Purchases.shared.access
                    row("Status", { switch access {
                        case .notStarted: return "Trial not started"
                        case .license(.unlocked): return "Unlocked"
                        case .license(.expired): return "Trial ended"
                        case .license(.trial(let d)): return d == 0 ? "Trial · last day" : "Trial · \(d) days left"
                    } }())
                    if access != .license(.unlocked) {
                        OutlineButton(title: "Unlock TextFox", color: Brand.green) { app.showUnlock = true }
                    }
                }

                group("About") {
                    HStack {
                        Text("Intro guide").font(.sans(16)).foregroundStyle(Brand.onSurface)
                        Spacer()
                        Button("Replay") { store.settings.onboarded = false }.font(.sans(15, medium: true)).foregroundStyle(Brand.green)
                    }
                    row("Version", Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "–")
                    row("Macros", "\(store.macros.count) · \(store.activeCount) live")
                    Link(destination: URL(string: "https://textfox.app")!) {
                        HStack {
                            Text("textfox.app").font(.sans(16)).foregroundStyle(Brand.onSurface)
                            Spacer()
                            Image(systemName: "arrow.up.right").foregroundStyle(Brand.green)
                        }
                    }
                    .buttonStyle(.plain)
                    Link(destination: URL(string: "mailto:hello@textfox.app")!) {
                        HStack {
                            Text("hello@textfox.app").font(.sans(16)).foregroundStyle(Brand.onSurface)
                            Spacer()
                            Image(systemName: "envelope").foregroundStyle(Brand.green)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 30)
        }
        .sheet(isPresented: $guide) { ShortcutsGuideView() }
        .fileExporter(isPresented: $exporting, document: backup, contentType: .json,
                      defaultFilename: "textfox-backup-\(CivilDate(Date(), in: .current))") { result in
            if case .success = result { app.show("Backup saved") }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data]) { result in
            guard case .success(let url) = result else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do {
                let n = try store.importBackup(Data(contentsOf: url))
                app.show("Imported \(n) macro\(n == 1 ? "" : "s")")
            } catch {
                app.show("That file isn't a TextFox backup")
            }
        }
    }

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.mono(14, medium: true)).foregroundStyle(Brand.onSurface)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LeafShape().fill(Brand.surface.opacity(0.9)))
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title).font(.sans(16)).foregroundStyle(Brand.onSurface)
            Spacer()
            Text(value).font(.mono(14)).foregroundStyle(Brand.mute)
        }
    }

    private func timeField(_ label: String, minutes: Binding<Int>) -> some View {
        HStack(spacing: 8) {
            Text(label).font(.sans(15)).foregroundStyle(Brand.mute)
            DatePicker("", selection: Binding(
                get: { Calendar.current.date(bySettingHour: minutes.wrappedValue / 60, minute: minutes.wrappedValue % 60, second: 0, of: Date()) ?? Date() },
                set: { let c = Calendar.current.dateComponents([.hour, .minute], from: $0); minutes.wrappedValue = (c.hour ?? 0) * 60 + (c.minute ?? 0) }
            ), displayedComponents: .hourAndMinute)
            .labelsHidden()
            .environment(\.locale, Locale(identifier: "en_GB"))
        }
    }
}

/// The backup as a document for the share/save sheet.
struct BackupFile: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

/// Background colour, brightness and card opacity, the presets, and the iPhone look.
struct AppearanceSection: View {
    @Environment(AppState.self) private var app
    @State private var draft = Look.current

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SwitchRow(title: "iPhone look", subtitle: "Calmer colours, Apple's system font and even corners. Off: the TextFox look from Android.",
                      isOn: Binding(get: { draft.iPhoneStyle }, set: { draft.iPhoneStyle = $0; app.look = draft }))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Look.presets, id: \.name) { p in
                        Button { draft = p.look; app.previewLook = nil; app.look = p.look } label: {
                            VStack(spacing: 6) {
                                BackdropView(look: p.look)
                                    .frame(width: 64, height: 64)
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(p.look == app.look ? Brand.green : Brand.hairline, lineWidth: p.look == app.look ? 2 : 1))
                                Text(p.name).font(.sans(12)).foregroundStyle(Brand.mute)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            slider("Hue", value: $draft.hue, in: 0...360) { "\(Int($0))°" }
            slider("Saturation", value: $draft.saturation, in: 0...2) { "\(Int($0 * 100))%" }
            slider("Brightness", value: $draft.brightness, in: 0.3...1.6) { "\(Int($0 * 100))%" }
            slider("Cards", value: $draft.cardOpacity, in: 0.4...1) { "\(Int($0 * 100))%" }
        }
        .onAppear { draft = app.look }
    }

    private func slider(_ title: String, value: Binding<Double>, in range: ClosedRange<Double>, label: @escaping (Double) -> String) -> some View {
        HStack(spacing: 12) {
            Text(title).font(.sans(15)).foregroundStyle(Brand.onSurface).frame(width: 86, alignment: .leading)
            Slider(value: value, in: range) { editing in
                // Live on the background while dragging; everything else follows on release.
                if editing { app.previewLook = draft } else { app.previewLook = nil; app.look = draft }
            }
            .onChange(of: value.wrappedValue) { if app.previewLook != nil { app.previewLook = draft } }
            .tint(Brand.green)
            Text(label(value.wrappedValue)).font(.mono(12)).foregroundStyle(Brand.mute).frame(width: 44, alignment: .trailing)
        }
    }
}
