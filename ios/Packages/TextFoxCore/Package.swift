// swift-tools-version: 6.0
import PackageDescription

// TextFox's platform-free logic: the macro model, scheduling, templates, matching, trial rules.
// Pure Swift with Foundation only, so `swift test` runs on a Mac without Xcode or a simulator.
let package = Package(
    name: "TextFoxCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "TextFoxCore", targets: ["TextFoxCore"])],
    targets: [
        .target(name: "TextFoxCore"),
        .testTarget(name: "TextFoxCoreTests", dependencies: ["TextFoxCore"])
    ]
)
