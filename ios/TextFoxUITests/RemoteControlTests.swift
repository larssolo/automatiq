@preconcurrency import XCTest

/// A development aid, not a test: drives the simulator from commands written to a file, so the
/// Shortcuts set-up can be walked through step by step (and photographed for the in-app guide).
/// Runs only with `TEST_RUNNER_REMOTE=<folder>`; write commands to <folder>/cmd.txt as "<n> <command>".
@MainActor
final class RemoteControlTests: XCTestCase {
    func testRemote() throws {
        continueAfterFailure = true
        guard let dir = ProcessInfo.processInfo.environment["REMOTE"] else { throw XCTSkip("Set TEST_RUNNER_REMOTE to drive the simulator") }
        let cmdURL = URL(fileURLWithPath: dir).appendingPathComponent("cmd.txt")
        let outURL = URL(fileURLWithPath: dir).appendingPathComponent("out.txt")
        var app = XCUIApplication(bundleIdentifier: "com.apple.shortcuts")
        // Commands from an earlier session are not run again.
        var last = (try? String(contentsOf: cmdURL, encoding: .utf8)) ?? ""
        try? "ready".write(to: outURL, atomically: true, encoding: .utf8)
        while true {
            guard let raw = try? String(contentsOf: cmdURL, encoding: .utf8), raw != last, let space = raw.firstIndex(of: " ") else {
                usleep(300_000); continue
            }
            last = raw
            let id = String(raw[..<space])
            let cmd = raw[raw.index(after: space)...].trimmingCharacters(in: .newlines)
            var out = "ok"
            let (verb, arg) = cmd.firstIndex(of: " ").map { (String(cmd[..<$0]), String(cmd[cmd.index(after: $0)...])) } ?? (cmd, "")
            switch verb {
            case "quit": try? "\(id)\nbye".write(to: outURL, atomically: true, encoding: .utf8); return
            case "app":
                app = arg == "textfox" ? XCUIApplication() : XCUIApplication(bundleIdentifier: arg)
            case "launch":
                if arg == "textfox" { app = XCUIApplication(); app.launchArguments = ["-paywall", "NO", "-skipIntro", "YES"]; app.launch() }
                else { app = XCUIApplication(bundleIdentifier: arg.isEmpty ? "com.apple.shortcuts" : arg); app.launch() }
            case "activate": app.activate()
            case "dump": out = app.debugDescription
            case "tree":
                // Just the labelled things, one per line: type, label, identifier, frame (one snapshot,
                // so a screen that changes meanwhile doesn't fail the run).
                var lines: [String] = []
                func walk(_ e: XCUIElementSnapshot) {
                    if !e.label.isEmpty || !e.identifier.isEmpty {
                        lines.append("\(e.elementType.rawValue)\t\(e.label)\t\(e.identifier)\t\(Int(e.frame.minX)),\(Int(e.frame.minY)),\(Int(e.frame.width))x\(Int(e.frame.height))")
                    }
                    e.children.forEach(walk)
                }
                if let snap = try? app.snapshot() { walk(snap) } else { out = "no snapshot" }
                if !lines.isEmpty { out = lines.joined(separator: "\n") }
            case "tap", "tapn":
                // tap <label> taps the first hittable element with that label or identifier; tapn <n> <label> the n-th.
                var label = arg; var n = 0
                if verb == "tapn", let sp = arg.firstIndex(of: " ") { n = Int(arg[..<sp]) ?? 0; label = String(arg[arg.index(after: sp)...]) }
                let q = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR identifier == %@", label, label))
                let all = q.allElementsBoundByIndex.filter { $0.isHittable }
                if all.count > n { all[n].tap() } else { out = "not found (\(q.count) total)" }
            case "tapxy":
                let p = arg.split(separator: ",").compactMap { Double($0) }
                app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: p[0], dy: p[1])).tap()
            case "drag":
                // drag x1,y1 x2,y2: press, hold and move (to reorder actions).
                let pts = arg.split(separator: " ").map { $0.split(separator: ",").compactMap { Double($0) } }
                let origin = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
                let from = origin.withOffset(CGVector(dx: pts[0][0], dy: pts[0][1]))
                let to = origin.withOffset(CGVector(dx: pts[1][0], dy: pts[1][1]))
                from.press(forDuration: 1.2, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.8)
            case "type": app.typeText(arg)
            case "swipeup": app.swipeUp()
            case "swipedown": app.swipeDown()
            case "wait": sleep(UInt32(arg) ?? 1)
            case "shot":
                let png = XCUIScreen.main.screenshot().pngRepresentation
                try? png.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(arg).png"))
            case "alert":
                let sb = XCUIApplication(bundleIdentifier: "com.apple.springboard")
                let b = sb.buttons[arg]
                if b.waitForExistence(timeout: 2) { b.tap() } else { out = "no alert button" }
            default: out = "unknown command"
            }
            try? "\(id)\n\(out)".write(to: outURL, atomically: true, encoding: .utf8)
        }
    }
}
