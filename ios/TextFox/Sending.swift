import ContactsUI
import MessageUI
import SwiftUI
import TextFoxCore

/// What the Messages sheet should send: the macro, its filled-in text and recipients.
struct OutgoingMessage: Identifiable {
    let id = UUID()
    let macro: Macro
    let body: String
    let recipients: [String]
    /// The planned send this is for, when it came from a notification.
    var fireId: String? = nil

    /// The macro's message with the variables filled in for [now] (the send time for a notification).
    init(macro: Macro, now: Date = Date(), fireId: String? = nil) {
        self.macro = macro
        self.fireId = fireId
        self.body = expandTemplate(macro.messageBody, now: now, macroName: macro.name,
                                   locale: Locale(identifier: "en_GB"))
        self.recipients = macro.recipients
    }

    static var canSend: Bool { MFMessageComposeViewController.canSendText() }
}

/// The system Messages sheet, pre-filled. iOS lets the user check it and tap Send; an app can't send
/// an SMS on its own (fully automatic sending goes through a Shortcuts automation, see PLAN.md).
struct MessageComposer: UIViewControllerRepresentable {
    let message: OutgoingMessage
    let onFinish: (MessageComposeResult) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let vc = MFMessageComposeViewController()
        vc.recipients = message.recipients
        vc.body = message.body
        vc.messageComposeDelegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: MFMessageComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let onFinish: (MessageComposeResult) -> Void
        init(onFinish: @escaping (MessageComposeResult) -> Void) { self.onFinish = onFinish }
        func messageComposeViewController(_ controller: MFMessageComposeViewController,
                                          didFinishWith result: MessageComposeResult) {
            onFinish(result)
        }
    }
}

/// The system contact picker; hands back the chosen phone number.
struct ContactPicker: UIViewControllerRepresentable {
    let onPick: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    func makeUIViewController(context: Context) -> CNContactPickerViewController {
        let vc = CNContactPickerViewController()
        vc.displayedPropertyKeys = [CNContactPhoneNumbersKey]
        vc.predicateForEnablingContact = NSPredicate(format: "phoneNumbers.@count > 0")
        vc.predicateForSelectionOfProperty = NSPredicate(format: "key == 'phoneNumbers'")
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: CNContactPickerViewController, context: Context) {}

    final class Coordinator: NSObject, CNContactPickerDelegate {
        let onPick: (String) -> Void
        init(onPick: @escaping (String) -> Void) { self.onPick = onPick }
        func contactPicker(_ picker: CNContactPickerViewController, didSelect property: CNContactProperty) {
            if let number = property.value as? CNPhoneNumber { onPick(number.stringValue) }
        }
        func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
            if let number = contact.phoneNumbers.first?.value { onPick(number.stringValue) }
        }
    }
}
