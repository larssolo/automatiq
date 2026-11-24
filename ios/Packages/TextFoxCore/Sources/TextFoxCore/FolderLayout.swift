import Foundation

// The folder-aware list as one flat row list, the same rules as the Android app's FolderLayout.kt.

public enum ListRow: Hashable, Sendable, Identifiable {
    case folder(Folder, memberCount: Int, activeCount: Int)
    case macro(Macro)

    public var id: String {
        switch self {
        case .folder(let f, _, _): "f:\(f.id)"
        case .macro(let m): "m:\(m.id)"
        }
    }
}

/// Root items (folders and folderless macros) share one sort space; each expanded folder's members
/// follow right after it. A macro whose folder no longer exists shows at root, so nothing disappears.
public func flattenRows(folders: [Folder], macros: [Macro]) -> [ListRow] {
    let folderIds = Set(folders.map(\.id))
    let members = Dictionary(grouping: macros.filter { $0.folderId.map(folderIds.contains) ?? false }) { $0.folderId! }
    enum Root { case folder(Folder), macro(Macro) }
    func key(_ r: Root) -> (Int, Int64) {
        switch r { case .folder(let f): (f.sortOrder, f.createdAt); case .macro(let m): (m.sortOrder, m.createdAt) }
    }
    let roots = (folders.map(Root.folder) + macros.filter { !($0.folderId.map(folderIds.contains) ?? false) }.map(Root.macro))
        .sorted { a, b in let ka = key(a), kb = key(b); return ka.0 != kb.0 ? ka.0 < kb.0 : ka.1 > kb.1 }
    var rows: [ListRow] = []
    for r in roots {
        switch r {
        case .folder(let f):
            let m = (members[f.id] ?? []).sorted { $0.sortOrder != $1.sortOrder ? $0.sortOrder < $1.sortOrder : $0.createdAt > $1.createdAt }
            rows.append(.folder(f, memberCount: m.count, activeCount: m.filter(\.enabled).count))
            if f.expanded { rows += m.map(ListRow.macro) }
        case .macro(let m): rows.append(.macro(m))
        }
    }
    return rows
}

/// Where a dropped macro belongs: the row right above it decides. An expanded folder or one of its
/// members puts it in that folder; anything else (a root macro, a closed folder, the top) is root.
public func folderForDrop(rows: [ListRow], at index: Int) -> String? {
    guard index > 0, index < rows.count else { return nil }
    switch rows[index - 1] {
    case .folder(let f, _, _): return f.expanded ? f.id : nil
    case .macro(let m):
        guard let fid = m.folderId else { return nil }
        let exists = rows.contains { if case .folder(let f, _, _) = $0 { f.id == fid } else { false } }
        return exists ? fid : nil
    }
}

/// The sort orders to save after a reorder: root items share one index space, and each folder's
/// visible members get their own, counted from 0.
public struct LayoutOrders: Equatable, Sendable {
    public var folderOrder: [String: Int] = [:]
    public var rootMacroOrder: [String: Int] = [:]
    public var memberOrder: [String: [String: Int]] = [:]
}

public func layoutOrders(_ rows: [ListRow]) -> LayoutOrders {
    var o = LayoutOrders()
    var rootIndex = 0
    for row in rows {
        switch row {
        case .folder(let f, _, _): o.folderOrder[f.id] = rootIndex; rootIndex += 1
        case .macro(let m):
            if let fid = m.folderId {
                o.memberOrder[fid, default: [:]][m.id] = o.memberOrder[fid]?.count ?? 0
            } else { o.rootMacroOrder[m.id] = rootIndex; rootIndex += 1 }
        }
    }
    return o
}

/// A folder's switch is on only when every member is on (an empty folder is off).
public func folderSwitchOn(memberCount: Int, activeCount: Int) -> Bool { memberCount > 0 && activeCount == memberCount }
