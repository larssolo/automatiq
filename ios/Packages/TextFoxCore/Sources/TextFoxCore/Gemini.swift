import Foundation

// Gemini, the same way the Android app calls it: the user's own free key, a model they choose, and
// a fallback to other models when one is out of quota or busy.

public let defaultGeminiModel = "gemini-3.6-flash"
public let geminiModels = ["gemini-3.6-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]
public let geminiKeyURL = URL(string: "https://aistudio.google.com/apikey")!
public let geminiKeyVideoURL = URL(string: "https://www.youtube.com/watch?v=ehTkQ3RNY6E")!

/// Tried after the user's model; each has its own free quota. 3.8 is left out (slow, often busy).
let geminiFallbackModels = ["gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]

func fallbackChain(_ preferred: String) -> [String] {
    var seen = Set<String>()
    return ([preferred] + geminiFallbackModels).filter { seen.insert($0).inserted }
}

private let legacyModels: Set<String> = ["gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash"]

/// Blank or retired → the default; a custom model name is kept.
public func resolveGeminiModel(_ saved: String?) -> String {
    let m = saved?.trimmingCharacters(in: .whitespaces) ?? ""
    return m.isEmpty || legacyModels.contains(m) ? defaultGeminiModel : m
}

// MARK: Wire format

struct GeminiRequest: Encodable {
    struct Part: Codable { let text: String }
    struct Content: Codable { let parts: [Part]; var role: String? = nil }
    struct Thinking: Encodable { var thinkingBudget: Int? = nil; var thinkingLevel: String? = nil }
    struct Config: Encodable { let maxOutputTokens: Int; let thinkingConfig: Thinking? }
    let systemInstruction: Content?
    let contents: [Content]
    let generationConfig: Config
}

private let minimalThinkingModels: Set<String> = ["gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]

/// Thinking shares the output budget with the reply, so it's turned off (or down) for short texts.
func thinkingConfig(for model: String) -> GeminiRequest.Thinking? {
    if minimalThinkingModels.contains(model) { return .init(thinkingLevel: "minimal") }
    if model.hasPrefix("gemini-3") { return .init(thinkingLevel: "low") }
    if model.hasPrefix("gemini-2.5") { return .init(thinkingBudget: 0) }
    return nil
}

func thinkingHeadroom(for model: String) -> Int { thinkingConfig(for: model)?.thinkingLevel == "low" ? 1024 : 0 }

func geminiRequestBody(system: String, user: String, model: String, maxTokens: Int) throws -> Data {
    let req = GeminiRequest(
        systemInstruction: system.isEmpty ? nil : .init(parts: [.init(text: system)]),
        contents: [.init(parts: [.init(text: user)])],
        generationConfig: .init(maxOutputTokens: maxTokens + thinkingHeadroom(for: model), thinkingConfig: thinkingConfig(for: model)))
    return try JSONEncoder().encode(req)
}

/// The generated text from a Gemini answer.
public func parseGeminiResponse(_ data: Data) throws -> String {
    struct R: Decodable {
        struct Candidate: Decodable { let content: GeminiRequest.Content }
        let candidates: [Candidate]?
    }
    let r = try JSONDecoder().decode(R.self, from: data)
    guard let text = r.candidates?.first?.content.parts.first?.text else { throw GeminiError.empty }
    return text.trimmingCharacters(in: .whitespacesAndNewlines)
}

func extractGeminiError(_ data: Data) -> String {
    struct E: Decodable { struct Body: Decodable { let message: String? }; let error: Body? }
    if let m = (try? JSONDecoder().decode(E.self, from: data))?.error?.message { return m }
    return String(decoding: data.prefix(200), as: UTF8.self)
}

public enum GeminiError: Error, Equatable, LocalizedError {
    case noKey
    case empty
    case http(Int, String)
    case timeout
    case offline
    case allModelsFailed(allQuota: Bool)

    /// Whether another model may succeed where this one failed (quota, overload, gone, timeout).
    var retryable: Bool {
        switch self {
        case .http(let code, _): code == 404 || code == 429 || code >= 500
        case .timeout: true
        default: false
        }
    }

    /// Short, for the log and toasts.
    public var errorDescription: String? {
        switch self {
        case .noKey: "No Gemini key — add one in Settings"
        case .empty: "Gemini sent an empty answer"
        case .http(let code, _) where [400, 401, 403].contains(code): "API key rejected (HTTP \(code))"
        case .http(429, _): "Free quota used up"
        case .http(let code, _): "Gemini error (HTTP \(code))"
        case .timeout: "Gemini didn't answer in time"
        case .offline: "No internet connection"
        case .allModelsFailed(let quota): quota ? "Free daily quota used up on all models" : "All Gemini models busy or unavailable"
        }
    }
}

public struct GeminiResult: Sendable, Equatable {
    public let text: String
    public let model: String
}

/// Tries each model until one answers. A non-retryable error (a bad key) stops right away.
func generateWithFallback(_ models: [String], call: (String) async throws -> String) async throws -> GeminiResult {
    var failures: [GeminiError] = []
    for model in models {
        do { return GeminiResult(text: try await call(model), model: model) }
        catch let e as GeminiError where e.retryable { failures.append(e) }
    }
    let allQuota = !failures.isEmpty && failures.allSatisfy { if case .http(429, _) = $0 { true } else { false } }
    throw GeminiError.allModelsFailed(allQuota: allQuota)
}

/// Calls Gemini with the user's key. The key goes in a header, never in the URL.
public struct GeminiClient: Sendable {
    public let apiKey: String
    public let model: String
    public init(apiKey: String, model: String?) { self.apiKey = apiKey; self.model = resolveGeminiModel(model) }

    public func generate(system: String, user: String, maxTokens: Int = 400) async throws -> GeminiResult {
        guard !apiKey.trimmingCharacters(in: .whitespaces).isEmpty else { throw GeminiError.noKey }
        return try await generateWithFallback(fallbackChain(model)) { m in try await call(m, system: system, user: user, maxTokens: maxTokens) }
    }

    private func call(_ model: String, system: String, user: String, maxTokens: Int) async throws -> String {
        var req = URLRequest(url: URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent")!,
                             timeoutInterval: 15)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        req.httpBody = try geminiRequestBody(system: system, user: user, model: model, maxTokens: maxTokens)
        let data: Data, response: URLResponse
        do { (data, response) = try await URLSession.shared.data(for: req) }
        catch let e as URLError where e.code == .timedOut { throw GeminiError.timeout }
        catch { throw GeminiError.offline }
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw GeminiError.http(code, extractGeminiError(data)) }
        return try parseGeminiResponse(data)
    }

    /// A fresh rewrite of a regular message (the AI variation of a scheduled macro).
    public func vary(_ message: String, instruction: String?) async throws -> GeminiResult {
        try await generate(system: varyPrompt(instruction: instruction), user: message, maxTokens: 300)
    }

    /// A reply to an incoming text (an AI auto-reply).
    public func reply(to incoming: String, instruction: String?, previous: String? = nil) async throws -> GeminiResult {
        try await generate(system: replyPrompt(instruction: instruction, previous: previous), user: incoming, maxTokens: 220)
    }

    /// Three suggestions for a message the user describes ("AI write" in the editor).
    public func suggest(_ description: String, style: String = "") async throws -> [String] {
        parseSuggestions(try await generate(system: suggestPrompt(style: style), user: description).text)
    }

    /// A tiny request that proves the key works.
    public func test() async throws -> GeminiResult {
        try await generate(system: "Reply with the single word OK.", user: "Test", maxTokens: 10)
    }
}

/// Same wording as Android's reply worker.
public func replyPrompt(instruction: String?, previous: String? = nil) -> String {
    var s = "You write ONE short SMS reply (at most 2 sentences). "
    s += "Reply ONLY with the message itself — no preamble, no explanation, no quotes. "
    s += "Reply in exactly the language of the incoming message — never a related language. "
    if let i = instruction?.trimmingCharacters(in: .whitespaces), !i.isEmpty { s += "Reply like this: \(i) " }
    if let p = previous { s += "Write a clearly different reply than this earlier one: \"\(p)\"" }
    return s
}

public func suggestPrompt(style: String) -> String {
    let st = style.trimmingCharacters(in: .whitespaces).isEmpty ? "" : " Style/tone: \(style)."
    return "You are an SMS assistant. The user describes a message they want to send. "
        + "Generate EXACTLY 3 short SMS variations in English (unless another language is specified).\(st) "
        + "Reply ONLY in this format — no introduction, no explanation:\n1. [message]\n2. [message]\n3. [message]"
}

/// "1. …\n2. …\n3. …" → the three texts; the whole answer when it isn't numbered.
public func parseSuggestions(_ raw: String) -> [String] {
    let items = raw.split(separator: "\n").compactMap { line -> String? in
        let t = line.trimmingCharacters(in: .whitespaces)
        guard t.count > 2, let first = t.first, first.isNumber, ("1"..."9").contains(String(first)), t.dropFirst().first == "." else { return nil }
        return t.dropFirst(2).trimmingCharacters(in: .whitespaces)
    }
    return items.isEmpty ? [raw.trimmingCharacters(in: .whitespacesAndNewlines)] : items
}
