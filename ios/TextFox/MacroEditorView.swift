import MapKit
import SwiftUI
import TextFoxCore

/// Create or edit a macro. The same sections as the Android editor: Macro, When, Do this, Send to,
/// Message and AI. Only the triggers an iPhone can run are offered for new macros.
struct MacroEditorView: View {
    let target: EditorTarget

    @Environment(MacroStore.self) private var store
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss

    @State private var d = EditorDraft()
    @State private var baseline = EditorDraft()
    @State private var loaded = false
    @State private var confirmDiscard = false
    @State private var confirmDelete = false
    @State private var showProblems = false
    @State private var sheet: PickerSheet?
    @State private var moreOpen = false
    @State private var contactIndex: Int?
    @State private var locating = false
    /// Until a place is chosen the map shows Denmark.
    @State private var guide = false
    @State private var camera: MapCameraPosition = .region(MKCoordinateRegion(
        center: .init(latitude: 56.0, longitude: 10.5), latitudinalMeters: 450_000, longitudinalMeters: 450_000))
    @FocusState private var messageFocused: Bool

    enum PickerSheet: String, Identifiable {
        case time, oneOffDate, startDate, endDate
        var id: String { rawValue }
    }

    private var existing: Macro? { if case .edit(let id) = target { store.macro(id: id) } else { nil } }
    private var accent: Color { Color(argb: d.cardColor) }
    private var hasChanges: Bool { loaded && d != baseline }
    private var today: CivilDate { CivilDate(Date(), in: .current) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    macroSection
                    whenSection
                    if !d.isReply { actionSection }
                    if d.recipientsRequired { recipientsSection }
                    if d.effectiveAction == .webhook { webhookSection }
                    if d.effectiveAction != .sound { messageSection }
                    if d.aiApplies { aiSection }
                    if showProblems && !d.canSave { problemsBox }
                    if d.effectiveAction != .sound || d.triggerType != .incoming { testButton }
                }
                .padding(.horizontal, 14)
                .padding(.top, 8)
                .padding(.bottom, 40)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Brand.sheet)
            .navigationTitle(existing == nil ? "New macro" : "Edit macro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Brand.sheet, for: .navigationBar)
            .toolbar { toolbar }
        }
        .interactiveDismissDisabled(hasChanges)
        .onAppear(perform: load)
        .sheet(item: $sheet) { which in pickerSheet(which) }
        .sheet(isPresented: $guide) { ShortcutsGuideView(focus: d.triggerType) }
        .sheet(item: Binding(get: { contactIndex.map(IdentifiedInt.init) }, set: { contactIndex = $0?.value })) { i in
            ContactPicker { number in
                if d.recipients.indices.contains(i.value) { d.recipients[i.value] = number }
            }
            .ignoresSafeArea()
        }
        .confirmationDialog("Discard changes?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Discard", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        } message: { Text("You have unsaved changes to this macro.") }
        .confirmationDialog("Delete this macro?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let m = existing {
                    store.delete(m)
                    app.show("\"\(m.name)\" deleted", actionLabel: "Undo") { store.restore(m) }
                }
                dismiss()
            }
        }
    }

    private func load() {
        guard !loaded else { return }
        if let m = existing { d = EditorDraft(m) }
        baseline = d
        loaded = true
        if let lat = d.latitude, let lon = d.longitude {
            camera = .region(MKCoordinateRegion(center: .init(latitude: lat, longitude: lon),
                                                latitudinalMeters: d.radiusMeters * 5, longitudinalMeters: d.radiusMeters * 5))
        }
    }

    private func save() {
        guard d.canSave else {
            withAnimation { showProblems = true }
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
            return
        }
        let m = d.toMacro(id: d.id ?? UUID().uuidString, today: today)
        store.upsert(m)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        dismiss()
    }

    // MARK: Toolbar

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button("Cancel") { if hasChanges { confirmDiscard = true } else { dismiss() } }
                .foregroundStyle(Brand.green)
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button(action: save) {
                Text("Save").font(.sans(15, medium: true)).foregroundStyle(.black)
                    .padding(.horizontal, 16).padding(.vertical, 6)
                    .background(Capsule().fill(d.canSave ? Brand.green : Brand.green.opacity(0.45)))
            }
            if let m = existing {
                Menu {
                    Button("Send now", systemImage: "paperplane.fill") { app.run(m); dismiss() }
                    Button("Copy", systemImage: "doc.on.doc") { store.duplicate(m); dismiss() }
                    Button("Delete", systemImage: "trash", role: .destructive) { confirmDelete = true }
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(Brand.onSurface)
                }
            }
        }
    }

    // MARK: Macro

    private var macroSection: some View {
        EditorSection(icon: "tag.fill", title: "MACRO", accent: accent) {
            HStack(spacing: 8) {
                Text("Active").font(.sans(14)).foregroundStyle(Brand.mute)
                ThemedToggle(isOn: $d.enabled)
            }
        } content: {
            TextField("", text: $d.name, prompt: Text("Name, e.g. Good morning, Mom").foregroundStyle(Brand.mute.opacity(0.7)))
                .font(.sans(17))
                .textInputAutocapitalization(.sentences)
                .accessibilityIdentifier("nameField")
                .padding(.horizontal, 14).padding(.vertical, 14)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.18)))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.white.opacity(0.35)))
            HStack {
                ForEach(cardColorPalette, id: \.self) { c in
                    let on = c == d.cardColor
                    Button { d.cardColor = c } label: {
                        ZStack {
                            Circle().fill(Color(argb: c))
                            if on {
                                Circle().stroke(.white, lineWidth: 2.5)
                                Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.black)
                            }
                        }
                        .frame(width: 26, height: 26)
                    }
                    .buttonStyle(.plain)
                    if c != cardColorPalette.last { Spacer(minLength: 0) }
                }
            }
        }
    }

    // MARK: When

    private var triggerOptions: [(value: TriggerType, icon: String, label: String)] {
        // Imported Android macros may use a trigger the iPhone can't run; keep it visible so it can be changed.
        TriggerType.allCases.filter { $0.supportedOnIOS || $0 == d.triggerType }.map { ($0, $0.icon, $0.label) }
    }

    private var whenSection: some View {
        EditorSection(icon: "bolt.fill", title: "WHEN", accent: accent) {
            ChoiceTiles(options: triggerOptions, selected: $d.triggerType, accent: accent)
            switch d.triggerType {
            case .scheduled: scheduleFields
            case .manual: Hint("Runs only when you start it — from the list, the widget or Shortcuts.")
            case .incoming: replyFields
            case .location: locationFields
            default:
                Hint("\(d.triggerType.label) macros run on Android only. On iPhone, build them with a Shortcuts automation, or pick another trigger.")
                    .foregroundStyle(Brand.amber)
            }
        }
    }

    @ViewBuilder private var scheduleFields: some View {
        HStack(alignment: .center) {
            Button { sheet = .time } label: {
                VStack(alignment: .leading, spacing: 0) {
                    Text(d.scheduledTime).font(.mono(40, medium: true)).foregroundStyle(.white)
                    Text("Tap to change").font(.sans(12, medium: true)).foregroundStyle(accent)
                }
            }
            .buttonStyle(.plain)
            Spacer()
            Segmented(options: [(false, "Repeat"), (true, "Once")], selected: Binding(
                get: { d.oneOff },
                set: { on in d.oneOff = on; if on && d.oneOffEpochDay == nil { d.oneOffEpochDay = today.epochDay } }
            ), accent: accent)
            .frame(width: 180)
        }
        if d.oneOff {
            OutlineButton(title: "On \(prettyDate(CivilDate(epochDay: d.oneOffEpochDay ?? today.epochDay)))") { sheet = .oneOffDate }
        } else {
            DayCircles(days: $d.daysOfWeek)
            if d.daysOfWeek.isEmpty { Text("Pick at least one day").font(.sans(12)).foregroundStyle(Brand.red) }
        }
        nextRunPreview
        HStack(spacing: 6) {
            Image(systemName: "bell.badge").foregroundStyle(Brand.mute)
            Text("A notification asks you to send.").font(.sans(13)).foregroundStyle(Brand.mute)
            Button("Send without a tap") { guide = true }.font(.sans(13, medium: true)).foregroundStyle(Brand.green)
        }
        moreOptions
    }

    private var nextRunPreview: some View {
        let m = d.toMacro(id: d.id ?? "preview", today: today)
        return TimelineView(.everyMinute) { ctx in
            HStack(spacing: 8) {
                Text("NEXT RUN").font(.mono(11)).tracking(1.5).foregroundStyle(Brand.mute)
                if !d.daysValid {
                    Text("—").font(.mono(13)).foregroundStyle(Brand.mute)
                } else if let next = m.nextFire(after: ctx.date) {
                    Text(formatNextFire(next, now: ctx.date)).font(.mono(13, medium: true)).foregroundStyle(Brand.green)
                } else {
                    Text("No more runs").font(.mono(13)).foregroundStyle(Brand.amber)
                }
            }
        }
    }

    @ViewBuilder private var moreOptions: some View {
        let extras = [
            !d.oneOff && d.randomDaysEnabled ? "\(d.randomDaysMin)–\(d.randomDaysMax) random days" : nil,
            !d.oneOff && d.weekInterval > 1 ? "every \(d.weekInterval) weeks" : nil,
            !d.oneOff ? d.validUntilEpochDay.map { "until \(prettyDate(CivilDate(epochDay: $0)))" } : nil,
            d.randomSpreadEnabled ? "±\(d.randomSpreadMinutes) min" : nil,
        ].compactMap { $0 }
        Button { withAnimation(.snappy) { moreOpen.toggle() } } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("More options").font(.sans(16)).foregroundStyle(Brand.onSurface)
                    Text(extras.isEmpty ? "Random days, every N weeks, end date, vary time" : extras.joined(separator: " · "))
                        .font(.sans(12)).foregroundStyle(Brand.mute).lineLimit(1)
                }
                Spacer()
                Image(systemName: "chevron.down").foregroundStyle(accent).rotationEffect(.degrees(moreOpen ? 180 : 0))
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.05)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("moreOptions")

        if moreOpen {
            VStack(alignment: .leading, spacing: 16) {
                if !d.oneOff {
                    SwitchRow(title: "Random days", subtitle: "Send on only some of the selected days — picked at random each week",
                              isOn: $d.randomDaysEnabled)
                    if d.randomDaysEnabled {
                        let maxDays = max(d.daysOfWeek.count, 1)
                        HStack(spacing: 8) {
                            Text("Between").foregroundStyle(Brand.onSurface)
                            numberMenu(d.randomDaysMin, 1...maxDays) { v in d.randomDaysMin = v; d.randomDaysMax = max(v, d.randomDaysMax) }
                            Text("and").foregroundStyle(Brand.onSurface)
                            numberMenu(d.randomDaysMax, 1...maxDays) { v in d.randomDaysMax = v; d.randomDaysMin = min(v, d.randomDaysMin) }
                            Text("days a week").foregroundStyle(Brand.onSurface)
                        }
                        .font(.sans(15))
                        if !d.randomDaysValid {
                            Text("Select at least \(d.randomDaysMax) days above").font(.sans(12)).foregroundStyle(Brand.red)
                        }
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Repeat").font(.sans(16)).foregroundStyle(Brand.onSurface)
                        Segmented(options: (1...4).map { ($0, $0 == 1 ? "Weekly" : "\($0) wk") }, selected: Binding(
                            get: { d.weekInterval },
                            set: { n in d.weekInterval = n; if n > 1 && d.startEpochDay == nil { d.startEpochDay = today.epochDay } }
                        ), accent: accent)
                    }
                    if d.weekInterval > 1 {
                        OutlineButton(title: "Counting from \(prettyDate(CivilDate(epochDay: d.startEpochDay ?? today.epochDay)))") { sheet = .startDate }
                    }
                    HStack {
                        OutlineButton(title: d.validUntilEpochDay.map { "Ends \(prettyDate(CivilDate(epochDay: $0)))" } ?? "Set end date (optional)") {
                            sheet = .endDate
                        }
                        if d.validUntilEpochDay != nil {
                            Button { d.validUntilEpochDay = nil } label: { Image(systemName: "xmark").foregroundStyle(Brand.onSurface) }
                                .accessibilityLabel("Clear end date")
                        }
                    }
                }
                SwitchRow(title: "Vary send time", subtitle: "Fire at a slightly different time each day", isOn: Binding(
                    get: { d.randomSpreadEnabled },
                    set: { on in d.randomSpreadEnabled = on; if on && d.randomSpreadMinutes < 1 { d.randomSpreadMinutes = 5 } }
                ))
                if d.randomSpreadEnabled {
                    Stepper(value: $d.randomSpreadMinutes, in: 1...120) {
                        Text("Within ±\(d.randomSpreadMinutes) min of \(d.scheduledTime)").font(.sans(15)).foregroundStyle(Brand.onSurface)
                    }
                }
            }
            .transition(.opacity.combined(with: .move(edge: .top)))
        }
    }

    private func numberMenu(_ value: Int, _ range: ClosedRange<Int>, set: @escaping (Int) -> Void) -> some View {
        Menu {
            ForEach(Array(range), id: \.self) { n in Button("\(n)") { set(n) } }
        } label: {
            Text("\(value)").font(.mono(15, medium: true)).foregroundStyle(accent)
                .frame(minWidth: 34, minHeight: 32)
                .background(RoundedRectangle(cornerRadius: 8).fill(accent.opacity(0.18)))
        }
    }

    @ViewBuilder private var replyFields: some View {
        Hint("Answers texts that match. On iPhone this runs through a Shortcuts automation (“When I get a message”).")
        Button("How to set it up") { guide = true }.font(.sans(14, medium: true)).foregroundStyle(Brand.green)
        FieldBox(label: "From (optional — blank = anyone)") {
            TextField("", text: $d.matchSender, prompt: Text("+45 12 34 56 78").foregroundStyle(Brand.mute.opacity(0.6)))
                .keyboardType(.phonePad)
        }
        FieldBox(label: "Contains (optional — blank = any text)") {
            TextField("", text: $d.matchKeyword, prompt: Text("e.g. where are you").foregroundStyle(Brand.mute.opacity(0.6)))
        }
    }

    @ViewBuilder private var locationFields: some View {
        Segmented(options: [(1, "On arrival"), (2, "On departure")], selected: $d.geofenceTransition, accent: accent)
        MapReader { proxy in
            Map(position: $camera) {
                if let lat = d.latitude, let lon = d.longitude {
                    let c = CLLocationCoordinate2D(latitude: lat, longitude: lon)
                    MapCircle(center: c, radius: d.radiusMeters).foregroundStyle(accent.opacity(0.25)).stroke(accent, lineWidth: 2)
                    Marker("", coordinate: c).tint(accent)
                }
            }
            .onTapGesture { p in
                if let c = proxy.convert(p, from: .local) { d.latitude = c.latitude; d.longitude = c.longitude }
            }
        }
        .frame(height: 200)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        Hint(d.latitude == nil ? "Tap the map to place the pin, or use where you are now." : "Tap the map to move the pin.")
        OutlineButton(title: locating ? "Finding you…" : "Use my current location", color: Brand.green) {
            locating = true
            Task {
                if let c = await LocationFetcher().current() {
                    d.latitude = c.latitude; d.longitude = c.longitude
                    camera = .region(MKCoordinateRegion(center: c, latitudinalMeters: d.radiusMeters * 5, longitudinalMeters: d.radiusMeters * 5))
                } else {
                    app.show("Couldn't get your location — allow it in Settings")
                }
                locating = false
            }
        }
        VStack(alignment: .leading, spacing: 6) {
            Text("Radius").font(.sans(16)).foregroundStyle(Brand.onSurface)
            Segmented(options: [(100.0, "100 m"), (200.0, "200 m"), (500.0, "500 m"), (1000.0, "1 km")], selected: $d.radiusMeters, accent: accent)
        }
    }

    // MARK: Do this

    private var actionSection: some View {
        EditorSection(icon: "play.fill", title: "DO THIS", accent: accent) {
            ChoiceTiles(options: [ActionType.sms, .reminder, .webhook].map { ($0, $0.icon, $0.label) }
                        + (d.actionType == .sound ? [(ActionType.sound, ActionType.sound.icon, ActionType.sound.label)] : []),
                        selected: $d.actionType, accent: accent, columns: d.actionType == .sound ? 4 : 3)
            switch d.effectiveAction {
            case .reminder: Hint("Shows a notification with your text — for you, not sent to anyone.")
            case .webhook: Hint("Calls a web address, e.g. to switch on a smart light. The message becomes the request body.")
            case .sound: Hint("iPhone apps can't change the sound mode. Use a Focus automation in Shortcuts instead.").foregroundStyle(Brand.amber)
            case .sms: EmptyView()
            }
        }
    }

    // MARK: Send to

    private var recipientsSection: some View {
        EditorSection(icon: "person.fill", title: "SEND TO", accent: accent) {
            ForEach(d.recipients.indices, id: \.self) { i in
                HStack(spacing: 10) {
                    FieldBox(label: "Number \(i + 1)") {
                        TextField("", text: Binding(get: { d.recipients.indices.contains(i) ? d.recipients[i] : "" },
                                                    set: { if d.recipients.indices.contains(i) { d.recipients[i] = $0 } }),
                                  prompt: Text("+45 12 34 56 78").foregroundStyle(Brand.mute.opacity(0.6)))
                            .keyboardType(.phonePad)
                            .accessibilityIdentifier("number\(i)")
                    }
                    VStack(spacing: 0) {
                        Spacer(minLength: 18)
                        HStack(spacing: 14) {
                            Button { contactIndex = i } label: { Image(systemName: "person.crop.circle").font(.system(size: 22)) }
                                .accessibilityLabel("Pick a contact")
                            if d.recipients.count > 1 {
                                Button { d.recipients.remove(at: i) } label: { Image(systemName: "minus.circle").font(.system(size: 20)) }
                                    .accessibilityLabel("Remove number")
                            }
                        }
                        .foregroundStyle(Brand.onSurface)
                        .frame(height: 46)
                    }
                }
            }
            Button { d.recipients.append("") } label: {
                Label("Add recipient", systemImage: "plus").font(.sans(15, medium: true)).foregroundStyle(Brand.green)
            }
            .padding(.leading, 4)
        }
    }

    // MARK: Webhook

    private var webhookSection: some View {
        EditorSection(icon: "link", title: "WEBHOOK", accent: accent) {
            FieldBox(label: "URL") {
                TextField("", text: $d.webhookUrl, prompt: Text("https://").foregroundStyle(Brand.mute.opacity(0.6)))
                    .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            Segmented(options: [("GET", "GET"), ("POST", "POST")], selected: $d.webhookMethod, accent: accent)
        }
    }

    // MARK: Message

    private var messageTitle: String {
        if d.isReply { return "REPLY" }
        switch d.effectiveAction {
        case .reminder: return "REMINDER TEXT"
        case .webhook: return "REQUEST BODY"
        default: return "MESSAGE"
        }
    }

    private var tokens: [String] { templateTokens + (d.isReply ? [senderToken] : []) }

    private var messageSection: some View {
        EditorSection(icon: "text.bubble", title: messageTitle, accent: accent) {
            if d.aiApplies {
                Button { app.show("AI writing arrives with the Gemini key in Settings") } label: {
                    Label("AI write", systemImage: "sparkles").font(.sans(14, medium: true)).foregroundStyle(Brand.green)
                }
            }
        } content: {
            VStack(alignment: .leading, spacing: 4) {
                TextField("", text: $d.message, prompt: Text(d.isReply ? "Your reply" : "Your message").foregroundStyle(Brand.mute.opacity(0.6)),
                          axis: .vertical)
                    .lineLimit(3...10)
                    .focused($messageFocused)
                    .accessibilityIdentifier("messageField")
                    .font(.sans(17))
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.18)))
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.white.opacity(0.35)))
                Text("\(d.message.count) characters").font(.sans(12)).foregroundStyle(Brand.mute).padding(.leading, 14)
            }
            Text("Tap to insert").font(.sans(12, medium: true)).foregroundStyle(Brand.mute)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tokens, id: \.self) { t in
                        Button { insert(t) } label: {
                            Text(tokenLabel(t)).font(.sans(14, medium: true)).foregroundStyle(Brand.onSurface)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.7)))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(1)
            }
            if !d.message.isEmpty {
                VStack(alignment: .trailing, spacing: 6) {
                    Text("Preview — right now").font(.sans(12)).foregroundStyle(Brand.mute)
                    TimelineView(.everyMinute) { ctx in
                        Text(expandTemplate(d.message, now: ctx.date, macroName: d.name, sender: d.isReply ? "+45 12 34 56 78" : nil,
                                            locale: Locale(identifier: "en_GB")))
                            .font(.sans(16)).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 4, topTrailingRadius: 18)
                                .fill(accent.opacity(0.45)))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
    }

    private func insert(_ token: String) {
        // SwiftUI's TextField doesn't expose the cursor, so tokens go at the end, with a space if needed.
        if let last = d.message.last, !last.isWhitespace { d.message += " " }
        d.message += token
    }

    // MARK: AI

    private var aiSection: some View {
        EditorSection(icon: "sparkles", title: d.isReply ? "AI REPLY" : "AI VARIATION", accent: accent) {
            ThemedToggle(isOn: $d.aiReplyEnabled)
        } content: {
            Hint(d.isReply
                 ? "Gemini writes each reply from the incoming text, guided by your message. If Gemini doesn't answer, your text is sent unchanged."
                 : "Every send gets a fresh rewrite of your message, so it never reads the same twice. If Gemini doesn't answer, your text is sent unchanged.")
            if d.aiReplyEnabled {
                Segmented(options: [(AiSendMode.approve, "Ask me first"), (AiSendMode.auto, "Send automatically")],
                          selected: $d.aiSendMode, accent: accent)
                Hint(d.aiSendMode == .approve
                     ? "A notification lets you review, edit or discard the text before it goes out."
                     : "The rewritten text goes out without asking you.")
                FieldBox(label: "Tone and style (optional)") {
                    TextField("", text: $d.aiReplyInstruction, prompt: Text("e.g. warm and short, vary the wording").foregroundStyle(Brand.mute.opacity(0.6)),
                              axis: .vertical)
                        .lineLimit(2...5)
                }
            }
        }
    }

    // MARK: Problems and test

    private var problemsBox: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(d.problems, id: \.self) { p in
                Label(p, systemImage: "exclamationmark.circle.fill").font(.sans(14)).foregroundStyle(Brand.red)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(Brand.red.opacity(0.12)))
    }

    private var testButton: some View {
        Button {
            guard d.canSave else { withAnimation { showProblems = true }; return }
            app.run(d.toMacro(id: d.id ?? UUID().uuidString, today: today))
        } label: {
            Label("Send now (test)", systemImage: "paperplane.fill")
                .font(.sans(16, medium: true)).foregroundStyle(Brand.onSurface)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(Capsule().fill(Color(hex: 0x4A4458)))
        }
        .buttonStyle(.plain)
        .opacity(d.isReply ? 0 : 1)
        .disabled(d.isReply)
    }

    // MARK: Pickers

    @ViewBuilder private func pickerSheet(_ which: PickerSheet) -> some View {
        switch which {
        case .time:
            TimePickerSheet(time: $d.scheduledTime)
        case .oneOffDate:
            DatePickerSheet(title: "Send on", epochDay: Binding(get: { d.oneOffEpochDay ?? today.epochDay }, set: { d.oneOffEpochDay = $0 }),
                            from: today)
        case .startDate:
            DatePickerSheet(title: "Count weeks from", epochDay: Binding(get: { d.startEpochDay ?? today.epochDay }, set: { d.startEpochDay = $0 }),
                            from: today.adding(days: -365))
        case .endDate:
            DatePickerSheet(title: "Last day", epochDay: Binding(get: { d.validUntilEpochDay ?? today.adding(days: 30).epochDay },
                                                                set: { d.validUntilEpochDay = $0 }), from: today)
        }
    }
}

private struct IdentifiedInt: Identifiable {
    let value: Int
    var id: Int { value }
}

private struct TimePickerSheet: View {
    @Binding var time: String
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            DatePicker("Time", selection: $date, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "en_GB"))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Brand.sheet)
                .navigationTitle("Send time").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                            time = String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
                            dismiss()
                        }
                    }
                }
        }
        .presentationDetents([.height(320)])
        .onAppear {
            if let t = HhMm(time) { date = Calendar.current.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: Date()) ?? Date() }
        }
    }
}

private struct DatePickerSheet: View {
    let title: String
    @Binding var epochDay: Int
    let from: CivilDate
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            DatePicker(title, selection: $date, in: from.at(hour: 0, minute: 0, in: .current)..., displayedComponents: .date)
                .datePickerStyle(.graphical)
                .padding()
                .frame(maxHeight: .infinity, alignment: .top)
                .background(Brand.sheet)
                .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { epochDay = CivilDate(date, in: .current).epochDay; dismiss() }
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .onAppear { date = CivilDate(epochDay: epochDay).at(hour: 12, minute: 0, in: .current) }
    }
}
