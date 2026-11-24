import SwiftUI
import TextFoxCore

/// Which editor is open: a new macro or an existing one.
enum EditorTarget: Identifiable {
    case new
    case edit(String)
    var id: String { if case .edit(let id) = self { id } else { "new" } }
}

/// The home screen: the fox, "N of M live", search, folders and macro cards, and the + button.
struct MacroListView: View {
    @Environment(MacroStore.self) private var store
    @Environment(AppState.self) private var app

    @State private var query = ""
    @State private var searchOpen = false
    @State private var reordering = false
    @State private var folderPrompt: FolderPrompt?
    @State private var folderName = ""
    @State private var deleteFolder: Folder?
    @State private var moveTarget: Macro?
    @FocusState private var searchFocused: Bool

    enum FolderPrompt: Identifiable {
        case create, rename(Folder)
        var id: String { if case .rename(let f) = self { f.id } else { "new" } }
    }

    private var searching: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            header
            if app.notificationStatus == .denied && store.macros.contains(where: { $0.enabled && $0.triggerType == .scheduled }) {
                notificationsOffBanner
            }
            content
        }
        .overlay(alignment: .bottom) { if !reordering { addButton.padding(.bottom, 14) } }
        .sheet(item: Binding(get: { app.editor }, set: { app.editor = $0 })) { target in
            MacroEditorView(target: target)
        }
        .alert(folderPromptTitle, isPresented: Binding(get: { folderPrompt != nil }, set: { if !$0 { folderPrompt = nil } })) {
            TextField("Name", text: $folderName).textInputAutocapitalization(.sentences)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                let name = folderName.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return }
                switch folderPrompt {
                case .create: store.createFolder(named: name)
                case .rename(let f): store.rename(f, to: name)
                case nil: break
                }
            }
        }
        .confirmationDialog(deleteFolder.map { "Delete \"\($0.name)\"?" } ?? "", isPresented: Binding(
            get: { deleteFolder != nil }, set: { if !$0 { deleteFolder = nil } }), titleVisibility: .visible
        ) {
            Button("Delete folder", role: .destructive) {
                guard let f = deleteFolder else { return }
                let members = store.delete(f)
                app.show("\"\(f.name)\" deleted", actionLabel: "Undo") { store.restore(f, members: members) }
            }
        } message: {
            Text("The macros move out to the top level — nothing is deleted.")
        }
        .confirmationDialog(moveTarget.map { "Move \"\($0.name)\" to…" } ?? "", isPresented: Binding(
            get: { moveTarget != nil }, set: { if !$0 { moveTarget = nil } }), titleVisibility: .visible
        ) {
            if let m = moveTarget {
                ForEach(store.folders.filter { $0.id != m.folderId }) { f in
                    Button(f.name) { store.move(m, toFolder: f.id) }
                }
                if m.folderId != nil { Button("No folder") { store.move(m, toFolder: nil) } }
            }
        }
    }

    private var folderPromptTitle: String {
        if case .rename = folderPrompt { "Rename folder" } else { "New folder" }
    }

    // MARK: Header

    @ViewBuilder private var header: some View {
        HStack(spacing: 10) {
            if searchOpen {
                Image(systemName: "magnifyingglass").foregroundStyle(Brand.mute).font(.system(size: 16))
                TextField("", text: $query, prompt: Text("Find a macro").foregroundStyle(Brand.mute))
                    .font(.mono(15))
                    .focused($searchFocused)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                Button { query = ""; searchOpen = false } label: {
                    Image(systemName: "xmark").foregroundStyle(Brand.onSurface)
                }
                .accessibilityLabel("Close search")
            } else {
                FoxLogo()
                    .fill(store.macros.contains(where: \.enabled) ? Brand.foxGreen : Brand.mute, style: FillStyle(eoFill: true))
                    .frame(width: 32, height: 32)
                    .accessibilityLabel("TextFox")
                Text("textfox").font(.mono(22, medium: true)).foregroundStyle(Brand.onSurface)
                Spacer()
                if !store.macros.isEmpty {
                    Text("\(store.activeCount) of \(store.macros.count) live")
                        .font(.mono(12)).foregroundStyle(Color(hex: 0xE6E6E6))
                    Button { searchOpen = true; searchFocused = true } label: {
                        Image(systemName: "magnifyingglass").foregroundStyle(Brand.mute)
                    }
                    .accessibilityLabel("Search macros")
                    Button { withAnimation { reordering.toggle() } } label: {
                        Text(reordering ? "Done" : "Sort")
                            .font(.sans(14, medium: true))
                            .foregroundStyle(reordering ? Brand.ink : Brand.green)
                            .padding(.horizontal, 10).padding(.vertical, 5)
                            .background(Capsule().fill(reordering ? Brand.green : Brand.green.opacity(0.12)))
                    }
                }
            }
        }
        .frame(minHeight: 44)
        .padding(.horizontal, 18)
        .padding(.top, 6)
    }

    private var notificationsOffBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "bell.slash.fill").foregroundStyle(Brand.amber)
            VStack(alignment: .leading, spacing: 2) {
                Text("Notifications are off").font(.sans(15, medium: true)).foregroundStyle(Brand.onSurface)
                Text("Scheduled macros can't tell you when it's time to send.").font(.sans(12)).foregroundStyle(Brand.mute)
            }
            Spacer(minLength: 0)
            Button("Turn on") { app.openSystemSettings() }
                .font(.sans(14, medium: true)).foregroundStyle(.black)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(Brand.amber))
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Brand.amber.opacity(0.12)))
        .padding(.horizontal, 10)
        .padding(.top, 6)
    }

    // MARK: List

    @ViewBuilder private var content: some View {
        if store.macros.isEmpty && store.folders.isEmpty {
            VStack(spacing: 6) {
                Text("Nothing automated yet.").font(.mono(16)).foregroundStyle(Brand.onSurface)
                Text("Tap + and teach your phone its first trick.").font(.sans(15)).foregroundStyle(Brand.mute)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if searching {
            let hits = store.macros.filter { $0.name.localizedCaseInsensitiveContains(query.trimmingCharacters(in: .whitespaces)) }
            if hits.isEmpty {
                Text("Nothing here called \"\(query.trimmingCharacters(in: .whitespaces))\".")
                    .font(.sans(15)).foregroundStyle(Brand.mute)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List { ForEach(hits) { macroRow($0, inFolder: nil) } }
                    .listStyle(.plain).scrollContentBackground(.hidden)
            }
        } else {
            List {
                ForEach(store.rows) { row in
                    switch row {
                    case .folder(let f, let count, let active): folderRow(f, count: count, active: active)
                    case .macro(let m): macroRow(m, inFolder: store.folders.first { $0.id == m.folderId })
                    }
                }
                .onMove { store.moveRows(from: $0, to: $1) }
                Color.clear.frame(height: 80).listRowBackground(Color.clear).listRowSeparator(.hidden)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .environment(\.editMode, .constant(reordering ? .active : .inactive))
        }
    }

    private func folderRow(_ f: Folder, count: Int, active: Int) -> some View {
        FolderCard(folder: f, memberCount: count, activeCount: active)
            .onTapGesture { withAnimation(.snappy) { store.toggleExpanded(f) } }
            .contextMenu {
                if count > 0 {
                    let on = folderSwitchOn(memberCount: count, activeCount: active)
                    Button(on ? "Disable all" : "Enable all", systemImage: on ? "pause.fill" : "play.fill") {
                        store.setAllEnabled(in: f, !on)
                    }
                }
                Button("Rename", systemImage: "pencil") { folderName = f.name; folderPrompt = .rename(f) }
                Button("Delete folder", systemImage: "trash", role: .destructive) { deleteFolder = f }
            }
            .rowStyle()
    }

    private func macroRow(_ m: Macro, inFolder folder: Folder?) -> some View {
        MacroCard(macro: m, veinColor: folder.map { Color(argb: $0.cardColor) }) { store.setEnabled(m, $0) }
            .padding(.leading, folder != nil && !searching ? 16 : 0)
            .onTapGesture { app.editor = .edit(m.id) }
            .contextMenu {
                Button("Send now", systemImage: "paperplane.fill") { app.run(m) }
                Button("Copy", systemImage: "doc.on.doc") { store.duplicate(m) }
                if !store.folders.isEmpty { Button("Move to folder…", systemImage: "folder") { moveTarget = m } }
                if m.folderId != nil { Button("Move out of folder", systemImage: "folder.badge.minus") { store.move(m, toFolder: nil) } }
                Button(m.enabled ? "Disable" : "Enable", systemImage: m.enabled ? "pause.fill" : "play.fill") {
                    store.setEnabled(m, !m.enabled)
                }
                Button("Delete", systemImage: "trash", role: .destructive) { delete(m) }
            }
            .swipeActions(edge: .trailing) {
                Button("Delete", systemImage: "trash", role: .destructive) { delete(m) }
                Button("Copy", systemImage: "doc.on.doc") { store.duplicate(m) }.tint(Color(hex: 0x3F51B5))
            }
            .swipeActions(edge: .leading) {
                Button("Send", systemImage: "paperplane.fill") { app.run(m) }.tint(Brand.foxGreen)
            }
            .rowStyle()
    }

    private func delete(_ m: Macro) {
        store.delete(m)
        app.show("\"\(m.name)\" deleted", actionLabel: "Undo") { store.restore(m) }
    }

    // MARK: Add

    private var addButton: some View {
        Menu {
            Button("New macro", systemImage: "bolt.fill") { app.editor = .new }
            Button("New folder", systemImage: "folder.badge.plus") { folderName = ""; folderPrompt = .create }
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.black)
                .frame(width: 62, height: 62)
                .background(BlobShape().fill(Brand.green))
                .shadow(color: Brand.green.opacity(0.35), radius: 12, y: 4)
        }
        .accessibilityLabel("New")
    }
}

private extension View {
    func rowStyle() -> some View {
        self.listRowInsets(EdgeInsets(top: 4, leading: 10, bottom: 4, trailing: 10))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}
