import SwiftUI
import TextFoxCore

// Names, icons and colours for macros, shared by the app and the widget.

extension TriggerType {
    var icon: String {
        switch self {
        case .scheduled: "clock"
        case .manual: "hand.tap"
        case .incoming: "arrowshape.turn.up.left.fill"
        case .location: "mappin.and.ellipse"
        case .charging: "battery.100percent.bolt"
        case .bluetooth: "antenna.radiowaves.left.and.right"
        case .wifi: "wifi"
        case .missedCall: "phone.arrow.down.left"
        }
    }

    var label: String {
        switch self {
        case .scheduled: "Scheduled"
        case .manual: "Manual"
        case .incoming: "Auto-reply"
        case .location: "Location"
        case .charging: "Charging"
        case .bluetooth: "Bluetooth"
        case .wifi: "Wi-Fi"
        case .missedCall: "Missed call"
        }
    }
}

extension ActionType {
    var icon: String {
        switch self {
        case .sms: "message.fill"
        case .sound: "speaker.wave.2.fill"
        case .reminder: "bell.badge.fill"
        case .webhook: "link"
        }
    }

    var label: String {
        switch self {
        case .sms: "Send SMS"
        case .sound: "Sound mode"
        case .reminder: "Reminder"
        case .webhook: "Webhook"
        }
    }
}

extension Macro {
    var accent: Color { Look.current.accent(Color(argb: accentColor)) }
    /// The card shows the trigger's icon for texts and the action's icon for everything else.
    var cardIcon: String {
        actionType == .sms || [.manual, .incoming, .missedCall].contains(triggerType) ? triggerType.icon : actionType.icon
    }
}
