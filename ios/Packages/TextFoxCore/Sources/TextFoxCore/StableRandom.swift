import Foundation

/// Java's `String.hashCode()`: Swift's own `hashValue` changes on every launch, which would make the
/// "random" send days and minutes jump around. This one is the same forever.
public func stableHash(_ s: String) -> Int32 {
    var h: Int32 = 0
    for unit in s.utf16 { h = h &* 31 &+ Int32(unit) }
    return h
}

/// SplitMix64: a tiny deterministic generator, so a seed always gives the same sequence.
public struct SeededGenerator: RandomNumberGenerator, Sendable {
    private var state: UInt64
    public init(seed: Int64) { state = UInt64(bitPattern: seed) }
    public mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

/// The seed for one macro on one day/week: macro id mixed with the epoch day.
func daySeed(_ seed: String, _ epochDay: Int) -> Int64 {
    Int64(stableHash(seed)) &* 1_000_003 &+ Int64(epochDay)
}
