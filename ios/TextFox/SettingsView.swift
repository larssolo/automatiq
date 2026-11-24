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

    var body: some View {
        @Bindable var store = store
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Settings").font(.sans(26, medium: true)).foregroundStyle(Brand.onSurface)

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

                group("About") {
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
