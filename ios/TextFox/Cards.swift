import SwiftUI
import TextFoxCore

/// One macro in the list: the breathing edge, the kind icon, name, summary, next run, AI mark and switch.
struct MacroCard: View {
    let macro: Macro
    var veinColor: Color? = nil
    let onToggle: (Bool) -> Void

    var body: some View {
        let accent = macro.accent
        HStack(spacing: 0) {
            BreathingVein(color: veinColor ?? accent, alive: macro.enabled)
            ZStack {
                Circle().fill(accent.opacity(0.18))
                Image(systemName: macro.cardIcon).font(.system(size: 17, weight: .semibold)).foregroundStyle(accent)
            }
            .frame(width: 38, height: 38)
            .padding(.leading, 12)
            .opacity(macro.enabled ? 1 : 0.5)

            VStack(alignment: .leading, spacing: 3) {
                Text(macro.name).font(.sans(16, medium: true)).foregroundStyle(Brand.onSurface).lineLimit(1)
                Text(macro.compactSummary()).font(.sans(12)).foregroundStyle(Brand.mute).lineLimit(1)
                if macro.triggerType == .scheduled && macro.enabled { NextRunLine(macro: macro) }
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .opacity(macro.enabled ? 1 : 0.5)

            if macro.aiReplyEnabled {
                Text("AI").font(.system(size: 11, weight: .bold)).foregroundStyle(accent)
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(RoundedRectangle(cornerRadius: 4).fill(accent.opacity(0.2)))
                    .frame(width: 44)
            }
            ThemedToggle(isOn: Binding(get: { macro.enabled }, set: onToggle), width: 42, height: 24)
                .padding(.trailing, 14)
                .padding(.leading, 4)
        }
        .frame(height: 76)
        .background(
            LinearGradient(colors: [accent.opacity(0.13), accent.opacity(0.02)], startPoint: .leading, endPoint: .trailing)
        )
        .background(Brand.surface.opacity(0.94))
        .clipShape(LeafShape())
        .contentShape(LeafShape())
    }
}

/// "▸ Next today 14:55", or "No more runs" once the end date has passed. Updates every minute.
private struct NextRunLine: View {
    let macro: Macro

    var body: some View {
        TimelineView(.everyMinute) { context in
            if let next = macro.nextFire(after: context.date) {
                Text("▸ Next \(formatNextFire(next, now: context.date))").foregroundStyle(Brand.green)
            } else {
                Text("No more runs").foregroundStyle(Brand.amber)
            }
        }
        .font(.mono(11))
        .lineLimit(1)
        .padding(.top, 1)
    }
}

/// A folder with its raised tab; tap opens or closes it.
struct FolderCard: View {
    let folder: Folder
    let memberCount: Int
    let activeCount: Int

    var body: some View {
        let accent = Color(argb: folder.cardColor == 0 ? cardColorPalette[3] : folder.cardColor)
        VStack(spacing: 0) {
            Color.clear.frame(height: FolderTabShape.tabHeight)
            HStack(spacing: 0) {
                BreathingVein(color: accent, alive: activeCount > 0)
                Image(systemName: "folder.fill").font(.system(size: 18)).foregroundStyle(accent).padding(.leading, 12)
                VStack(alignment: .leading, spacing: 3) {
                    Text(folder.name).font(.sans(16, medium: true)).foregroundStyle(Brand.onSurface).lineLimit(1)
                    Text(memberCount == 0 ? "Empty folder — move macros in" : "\(memberCount) macros · \(activeCount) active")
                        .font(.sans(12)).foregroundStyle(Brand.mute).lineLimit(1)
                }
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    Circle().fill(accent.opacity(0.16))
                    Image(systemName: "chevron.right").font(.system(size: 14, weight: .bold)).foregroundStyle(accent)
                        .rotationEffect(.degrees(folder.expanded ? 90 : 0))
                }
                .frame(width: 36, height: 36)
                .padding(.trailing, 14)
            }
            .frame(maxHeight: .infinity)
        }
        .frame(height: 88)
        .background(alignment: .topLeading) {
            GeometryReader { g in
                Rectangle().fill(accent.opacity(0.45))
                    .frame(width: g.size.width * FolderTabShape.tabWidth, height: FolderTabShape.tabHeight)
            }
        }
        .background(
            LinearGradient(colors: [accent.opacity(0.16), accent.opacity(0.03)], startPoint: .leading, endPoint: .trailing)
        )
        .background(Brand.surface.opacity(0.94))
        .clipShape(FolderTabShape())
        .contentShape(FolderTabShape())
    }
}
