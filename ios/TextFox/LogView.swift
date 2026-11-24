import SwiftUI
import TextFoxCore

/// What ran and how it went, newest first, filterable by status and by macro.
struct LogView: View {
    @Environment(MacroStore.self) private var store
    @State private var status: MacroStatus?
    @State private var macroId: String?
    @State private var confirmClear = false

    private var entries: [LogEntry] {
        store.log.filter { (status == nil || $0.status == status) && (macroId == nil || $0.macroId == macroId) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Log").font(.sans(26, medium: true)).foregroundStyle(Brand.onSurface)
                Spacer()
                if !store.log.isEmpty {
                    Button("Clear") { confirmClear = true }.font(.sans(15, medium: true)).foregroundStyle(Brand.green)
                }
            }
            HStack(spacing: 8) {
                chip("All", nil)
                chip("Success", .success)
                chip("Failed", .failed)
                chip("Pending", .pending)
            }
            Menu {
                Button("All macros") { macroId = nil }
                ForEach(Array(Set(store.log.map(\.macroId))).sorted(), id: \.self) { id in
                    Button(store.log.first { $0.macroId == id }?.macroName ?? id) { macroId = id }
                }
            } label: {
                HStack {
                    Text(macroId.flatMap { id in store.log.first { $0.macroId == id }?.macroName } ?? "All macros")
                        .font(.sans(16)).foregroundStyle(Brand.onSurface)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down").foregroundStyle(Brand.mute)
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.white.opacity(0.35)))
            }

            if entries.isEmpty {
                VStack(spacing: 6) {
                    Text(store.log.isEmpty ? "Nothing has run yet." : "Nothing matches.").font(.mono(15)).foregroundStyle(Brand.onSurface)
                    Text("Sends, reminders and webhooks show up here.").font(.sans(14)).foregroundStyle(Brand.mute)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(entries) { e in
                            LogRow(entry: e)
                            Divider().overlay(Color.white.opacity(0.08))
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 6)
        .confirmationDialog("Clear the log?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Clear log", role: .destructive) { store.clearLog() }
        }
    }

    private func chip(_ title: String, _ value: MacroStatus?) -> some View {
        let on = status == value
        return Button { status = value } label: {
            Text(title).font(.mono(13)).foregroundStyle(on ? Brand.onSurface : Brand.mute)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 8).fill(on ? Color(hex: 0x3A3546) : .clear))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(on ? .clear : Color.white.opacity(0.3)))
        }
        .buttonStyle(.plain)
    }
}

private struct LogRow: View {
    let entry: LogEntry

    private static let time: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "dd/MM HH:mm:ss"
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text("\(entry.status.rawValue) · \(entry.macroName)").font(.mono(13)).foregroundStyle(color).lineLimit(1)
                Spacer()
                Text(Self.time.string(from: entry.at)).font(.mono(12)).foregroundStyle(Brand.mute)
            }
            Text(entry.text).font(.sans(15)).foregroundStyle(Brand.onSurface)
            if let detail = entry.detail { Text(detail).font(.sans(12)).foregroundStyle(Brand.mute) }
        }
        .padding(.vertical, 12)
    }

    private var color: Color {
        switch entry.status {
        case .success: Brand.green
        case .failed: Brand.red
        case .pending: Brand.amber
        }
    }
}
