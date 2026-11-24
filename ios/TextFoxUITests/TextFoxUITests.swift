import XCTest

/// Walks through every screen in the simulator. Fails on a crash or a missing control, and saves a
/// screenshot of each step to $SHOT_DIR when it's set (pass it as TEST_RUNNER_SHOT_DIR).
final class TextFoxUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-resetData", "YES"]
        app.launch()
        allowNotifications()
    }

    /// The first launch asks for notifications (the demo has scheduled macros); say yes.
    private func allowNotifications() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allow = springboard.buttons["Allow"]
        if allow.waitForExistence(timeout: 3) { allow.tap() }
    }

    private func shot(_ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SHOT_DIR"] {
            try? png.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
    }

    func testListShowsDemoMacrosAndFolders() {
        XCTAssertTrue(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Family"].exists)
        XCTAssertTrue(app.staticTexts["5 of 6 live"].exists)
        shot("01-list")

        // Closing a folder hides its macros.
        app.staticTexts["Family"].tap()
        XCTAssertFalse(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 1))
        shot("02-folder-closed")
        app.staticTexts["Family"].tap()
        XCTAssertTrue(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 2))

        // Search.
        app.buttons["Search macros"].tap()
        app.typeText("late")
        XCTAssertTrue(app.staticTexts["Running late"].waitForExistence(timeout: 2))
        XCTAssertFalse(app.staticTexts["Kids' bedtime"].exists)
        shot("03-search")
        app.buttons["Close search"].tap()
    }

    func testEditorSectionsAndTriggers() {
        app.staticTexts["Good morning, Mom"].tap()
        XCTAssertTrue(app.staticTexts["MACRO"].waitForExistence(timeout: 3))
        shot("10-editor-top")

        app.buttons["moreOptions"].tap()
        XCTAssertTrue(app.staticTexts["Random days"].waitForExistence(timeout: 2))
        shot("11-editor-more")

        app.swipeUp(); app.swipeUp()
        XCTAssertTrue(app.staticTexts["MESSAGE"].waitForExistence(timeout: 2))
        shot("12-editor-message")
        app.swipeUp(); app.swipeUp()
        XCTAssertTrue(app.staticTexts["AI VARIATION"].waitForExistence(timeout: 2))
        shot("13-editor-ai")

        app.swipeDown(); app.swipeDown(); app.swipeDown(); app.swipeDown(); app.swipeDown()
        for trigger in ["Manual", "Auto-reply", "Location"] {
            app.buttons[trigger].tap()
            if trigger == "Location" { sleep(5) } // let the map tiles load
            shot("14-trigger-\(trigger)")
        }
        // Back to where it was: no changes, so Cancel just closes.
        app.buttons["Scheduled"].tap()
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 2))

        // Leaving with changes asks first.
        app.staticTexts["Good morning, Mom"].tap()
        let name = app.textFields["nameField"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap(); name.typeText("!")
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["Discard"].waitForExistence(timeout: 2))
        app.buttons["Discard"].tap()
        XCTAssertTrue(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 2))
    }

    func testCreateMacroThenDeleteAndUndo() {
        app.buttons["New"].tap()
        app.buttons["New macro"].tap()
        XCTAssertTrue(app.staticTexts["New macro"].waitForExistence(timeout: 3))

        // Saving an empty form lists what's missing.
        app.buttons["Save"].tap()
        app.swipeUp(); app.swipeUp(); app.swipeUp()
        XCTAssertTrue(app.staticTexts["Give the macro a name"].waitForExistence(timeout: 2))
        shot("20-new-problems")
        app.swipeDown(); app.swipeDown(); app.swipeDown()

        let name = app.textFields["nameField"]
        name.tap(); name.typeText("Hello Dad")
        app.buttons["Manual"].tap()
        let number = app.textFields["number0"]
        number.tap(); number.typeText("+4511223344")
        let message = app.textFields["messageField"]
        message.tap(); message.typeText("Hi Dad, it's {ugedag}!")
        shot("21-new-filled")
        app.buttons["Save"].tap()

        let card = app.staticTexts["Hello Dad"]
        XCTAssertTrue(card.waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["6 of 7 live"].exists)
        shot("22-new-saved")

        card.press(forDuration: 1.0)
        app.buttons["Delete"].tap()
        XCTAssertTrue(app.buttons["Undo"].waitForExistence(timeout: 2))
        shot("23-deleted")
        app.buttons["Undo"].tap()
        XCTAssertTrue(card.waitForExistence(timeout: 2))
    }

    func testSendNowLogsInTheSimulator() {
        let card = app.staticTexts["Running late"]
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        card.press(forDuration: 1.5)
        if !app.buttons["Send now"].waitForExistence(timeout: 2) { card.press(forDuration: 1.5) }
        app.buttons["Send now"].tap()
        // The simulator has no Messages, so the attempt is logged as failed.
        app.buttons["Log"].tap()
        XCTAssertTrue(app.staticTexts["FAILED · Running late"].waitForExistence(timeout: 3))
        shot("30-log")
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Quiet hours"].waitForExistence(timeout: 2))
        shot("31-settings")
    }

    func testScheduledNotificationIsDelivered() {
        XCTAssertTrue(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 5))
        // Debug link: a send notification for this macro in 3 seconds.
        app.terminate()
        app.launchArguments = ["-open", "textfox://test-fire/Running late"]
        app.launch()
        XCUIDevice.shared.press(.home)
        sleep(6)
        // Open Notification Center (swipe down from the top) and find it.
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.005))
            .press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.7)))
        let note = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Running about 10 minutes late")).firstMatch
        XCTAssertTrue(note.waitForExistence(timeout: 8))
        shot("40-notification")
        XCUIDevice.shared.press(.home)
    }

    func testNotificationSendAndSkipAreLogged() {
        XCTAssertTrue(app.staticTexts["Good morning, Mom"].waitForExistence(timeout: 5))
        app.terminate()
        // (Names without apostrophes: the test runner escapes them in launch arguments.)
        app.launchArguments = ["-open", "textfox://test-tap/Running late"]
        app.launch()
        // The link is handled on appear (and switches to the list); wait for that before opening the log.
        XCTAssertTrue(app.staticTexts["Running late"].waitForExistence(timeout: 5))
        sleep(1)
        // The simulator can't send texts, so Send logs the planned send as failed.
        app.buttons["Log"].tap()
        XCTAssertTrue(app.staticTexts["FAILED · Running late"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["This device can't send text messages"].exists)
        app.terminate()
        app.launchArguments = ["-open", "textfox://test-skip/Good morning, Mom"]
        app.launch()
        sleep(2)
        app.buttons["Log"].tap()
        XCTAssertTrue(app.staticTexts["Skipped"].waitForExistence(timeout: 5))
        shot("41-notification-logged")
    }
}
