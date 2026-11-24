import Foundation

/// The App Group the app, the widget and the Shortcuts actions share.
public let appGroupId = "group.app.kodekid.textfox"

/// Where TextFox keeps its data: the App Group container, or the app's own Documents folder when the
/// group isn't available (an unsigned simulator build).
public func textFoxStoreURL(fileName: String = "textfox.json") -> URL {
    let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId)
        ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    return dir.appendingPathComponent(fileName)
}

/// The macros and folders from the store file, for readers that don't own it (the widget, Shortcuts).
public struct StoredMacros: Decodable, Sendable {
    public var macros: [Macro] = []
    public var folders: [Folder] = []

    enum CodingKeys: String, CodingKey { case macros, folders }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        macros = try c.decodeIfPresent([Macro].self, forKey: .macros) ?? []
        folders = try c.decodeIfPresent([Folder].self, forKey: .folders) ?? []
    }

    public init() {}

    /// Empty when the file is missing or unreadable.
    public static func load(from url: URL = textFoxStoreURL()) -> StoredMacros {
        guard let data = try? Data(contentsOf: url) else { return StoredMacros() }
        return (try? JSONDecoder().decode(StoredMacros.self, from: data)) ?? StoredMacros()
    }

    /// Root order first, then folder members, the way the list shows them.
    public var ordered: [Macro] {
        flattenRows(folders: folders.map { var f = $0; f.expanded = true; return f }, macros: macros).compactMap {
            if case .macro(let m) = $0 { m } else { nil }
        }
    }
}

public extension Macro {
    /// The macro that answers an incoming text, if any: enabled auto-replies whose filters match.
    static func replyMacro(in macros: [Macro], sender: String, body: String) -> Macro? {
        macros.first { $0.enabled && $0.triggerType == .incoming && $0.matchesIncoming(sender: sender, body: body) }
    }
}
