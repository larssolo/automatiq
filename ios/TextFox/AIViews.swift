import SwiftUI
import TextFoxCore

/// Settings → AI: the user's own free Gemini key, the model, and the consent to send text to Google.
struct AISettingsSection: View {
    @Environment(MacroStore.self) private var store
    @Environment(AppState.self) private var app
    @State private var key = GeminiKey.value
    @State private var testing = false
    @State private var result: String?
    @State private var ok = false

    var body: some View {
        @Bindable var store = store
        VStack(alignment: .leading, spacing: 14) {
            Hint("AI variation, AI replies and AI write use Google Gemini with your own free key. TextFox has no server: the text goes straight from your phone to Google.")
            HStack(spacing: 14) {
                Link(destination: geminiKeyURL) { Label("Get a free key", systemImage: "key.fill") }
                Link(destination: geminiKeyVideoURL) { Label("49-sec video", systemImage: "play.rectangle.fill") }
            }
            .font(.sans(14, medium: true)).foregroundStyle(Brand.green)
            FieldBox(label: "Gemini API key") {
                SecureField("", text: $key, prompt: Text("Paste your key").foregroundStyle(Brand.mute.opacity(0.6)))
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .onChange(of: key) { _, v in GeminiKey.value = v; result = nil }
                    .accessibilityIdentifier("geminiKey")
            }
            HStack {
                Text("Model").font(.sans(15)).foregroundStyle(Brand.onSurface)
                Spacer()
                Picker("Model", selection: $store.settings.geminiModel) {
                    ForEach(geminiModels, id: \.self) { Text($0).tag($0) }
                }
                .tint(Brand.green)
            }
            SwitchRow(title: "Allow AI", subtitle: "I agree that the message text of AI macros is sent to Google Gemini.",
                      isOn: $store.settings.aiConsent)
            HStack {
                OutlineButton(title: testing ? "Testing…" : "Test the key", color: Brand.green) {
                    testing = true
                    Task {
                        do { let r = try await GeminiClient(apiKey: key, model: store.settings.geminiModel).test(); ok = true; result = "Works · \(r.model)" }
                        catch { ok = false; result = error.localizedDescription }
                        testing = false
                    }
                }
                .disabled(key.isEmpty || testing)
            }
            if let result {
                Label(result, systemImage: ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .font(.sans(14)).foregroundStyle(ok ? Brand.green : Brand.amber)
            }
        }
    }
}

/// "AI write": describe the message, get three suggestions, tap one to use it.
struct AIWriteSheet: View {
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var describe = ""
    @State private var style = ""
    @State private var busy = false
    @State private var options: [String] = []
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    FieldBox(label: "What should the message say?") {
                        TextField("", text: $describe, prompt: Text("e.g. tell mom I'll be home late, ask if she needs anything").foregroundStyle(Brand.mute.opacity(0.6)),
                                  axis: .vertical).lineLimit(2...5)
                    }
                    FieldBox(label: "Tone (optional)") {
                        TextField("", text: $style, prompt: Text("e.g. warm and short").foregroundStyle(Brand.mute.opacity(0.6)))
                    }
                    if !AI.ready {
                        Hint(GeminiKey.value.isEmpty ? "Add your free Gemini key in Settings → AI first." : "Switch on “Allow AI” in Settings → AI first.")
                            .foregroundStyle(Brand.amber)
                    }
                    Button {
                        busy = true; error = nil
                        Task {
                            do { options = try await AI.client.suggest(describe, style: style) }
                            catch let e { error = e.localizedDescription }
                            busy = false
                        }
                    } label: {
                        Label(busy ? "Writing…" : "Write 3 suggestions", systemImage: "sparkles")
                            .font(.sans(16, medium: true)).foregroundStyle(.black)
                            .frame(maxWidth: .infinity, minHeight: 50).background(Capsule().fill(Brand.green))
                    }
                    .disabled(describe.trimmingCharacters(in: .whitespaces).isEmpty || busy || !AI.ready)
                    if let error { Label(error, systemImage: "exclamationmark.triangle.fill").font(.sans(14)).foregroundStyle(Brand.amber) }
                    ForEach(options, id: \.self) { o in
                        Button { onPick(o); dismiss() } label: {
                            Text(o).font(.sans(16)).foregroundStyle(.white).multilineTextAlignment(.leading)
                                .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 14).fill(Brand.green.opacity(0.14)))
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Brand.green.opacity(0.4)))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(18)
            }
            .background(Brand.sheet)
            .navigationTitle("AI write").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }
}
