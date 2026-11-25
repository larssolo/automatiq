import AppIntents
import TextFoxCore

/// A macro as Shortcuts and the widget's settings see it.
struct MacroEntity: AppEntity, Identifiable {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "TextFox Macro")
    static let defaultQuery = MacroQuery()

    var id: String
    var name: String
    var summary: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)", subtitle: "\(summary)")
    }

    init(_ m: Macro) { id = m.id; name = m.name; summary = m.compactSummary() }
}

/// Reads the macros from the shared store file, so it works in the app and in the widget.
struct MacroQuery: EntityStringQuery {
    func entities(for identifiers: [String]) async throws -> [MacroEntity] {
        let all = StoredMacros.load().macros
        return identifiers.compactMap { id in all.first { $0.id == id }.map(MacroEntity.init) }
    }

    func entities(matching string: String) async throws -> [MacroEntity] {
        StoredMacros.load().ordered.filter { $0.name.localizedCaseInsensitiveContains(string) }.map(MacroEntity.init)
    }

    func suggestedEntities() async throws -> [MacroEntity] {
        StoredMacros.load().ordered.map(MacroEntity.init)
    }
}
