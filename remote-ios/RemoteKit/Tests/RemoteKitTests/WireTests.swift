import XCTest
@testable import RemoteKit

/// The protocol is the contract between three codebases, so the client's codec is pinned by tests:
/// every frame it can produce, and every frame a TV can send it.
final class WireTests: XCTestCase {

    func testHelloOmitsTokenOnFirstConnect() throws {
        let first = Wire.hello(deviceId: "abc", name: "iPhone", platform: "ios", app: "Couch Remote/1.0", token: nil)
        let object = try parse(first)
        XCTAssertNil(object["token"])
        XCTAssertEqual(object["v"] as? Int, 1)
        XCTAssertEqual(object["t"] as? String, "hello")

        let paired = Wire.hello(deviceId: "abc", name: "iPhone", platform: "ios", app: "Couch Remote/1.0", token: "tok")
        XCTAssertEqual(try parse(paired)["token"] as? String, "tok")
    }

    func testEveryKeyEncodesToItsWireName() throws {
        for key in Wire.Key.allCases {
            let object = try parse(Wire.key(key))
            XCTAssertEqual(object["k"] as? String, key.rawValue)
            XCTAssertNil(object["hold"])
        }
        XCTAssertEqual(try parse(Wire.key(.ok, hold: true))["hold"] as? Bool, true)
    }

    func testPointerIsClampedAndBatched() throws {
        let pointer = try parse(Wire.pointer(dx: 9.5, dy: -9.5, ts: 7))
        XCTAssertEqual(pointer["dx"] as? Double, 1)
        XCTAssertEqual(pointer["dy"] as? Double, -1)
        XCTAssertEqual(pointer["ts"] as? Int, 7)
        XCTAssertNil(pointer["batch"])

        let samples = (0..<20).map { Wire.PointerSample(dx: 0.01, dy: 0.02, ts: Int64($0)) }
        let batch = try XCTUnwrap(Wire.pointerBatch(samples))
        let parsed = try parse(batch)
        let list = try XCTUnwrap(parsed["batch"] as? [[Any]])
        XCTAssertEqual(list.count, Wire.maxPointerBatch)
        XCTAssertEqual(list.first?[0] as? Double, 0.01)

        XCTAssertNil(Wire.pointerBatch([]))
    }

    func testTextIsBoundedAndEmptyTextIsDropped() {
        XCTAssertNil(Wire.text(""))
        XCTAssertNotNil(Wire.text("   ")) // spaces are legitimate input
        guard let long = Wire.text(String(repeating: "a", count: 900)) else {
            return XCTFail("long text should still encode, truncated")
        }
        let parsed = try? parse(long)
        XCTAssertEqual((parsed?["v"] as? String)?.count, Wire.maxText)
        XCTAssertNil(Wire.voice("   "))
        XCTAssertNotNil(Wire.voice("the bear"))
        XCTAssertNotNil(Wire.launch(package: "com.netflix.mediaclient"))
        XCTAssertNil(Wire.launch(package: ""))
    }

    func testBackspaceIsClamped() throws {
        XCTAssertEqual(try parse(Wire.backspace(0))["n"] as? Int, 1)
        XCTAssertEqual(try parse(Wire.backspace(999))["n"] as? Int, Wire.maxBackspace)
    }

    func testDecodesWelcomeWithSizeAndCapabilities() throws {
        let frame = """
        {"t":"welcome","v":1,"device":"9f2c","token":"tok","tv":{"name":"Living room","model":"Android TV","w":1920,"h":1080},"caps":["dpad","pointer","voice"]}
        """
        guard case let .welcome(welcome)? = Wire.decode(frame) else {
            return XCTFail("expected a welcome")
        }
        XCTAssertEqual(welcome.token, "tok")
        XCTAssertEqual(welcome.name, "Living room")
        XCTAssertEqual(welcome.width, 1920)
        XCTAssertTrue(welcome.caps.contains("pointer"))
    }

    func testDecodesPairRequiredAndErrors() throws {
        guard case let .pairRequired(device, name, digits)? = Wire.decode("""
        {"t":"pair-required","device":"d1","tvName":"Living room","pin":6}
        """) else { return XCTFail("expected pair-required") }
        XCTAssertEqual(device, "d1")
        XCTAssertEqual(name, "Living room")
        XCTAssertEqual(digits, 6)

        guard case let .error(code, message, attempts)? = Wire.decode("""
        {"t":"error","code":"pin","message":"That code didn't work","attempts":2}
        """) else { return XCTFail("expected an error") }
        XCTAssertEqual(code, "pin")
        XCTAssertEqual(message, "That code didn't work")
        XCTAssertEqual(attempts, 2)
    }

    func testDecodesStateAndApps() throws {
        let frame = """
        {"t":"state","screen":"home","focus":{"kind":"app","label":"Netflix"},
         "media":{"playing":true,"title":"The Bear","subtitle":"S2 E4","positionMs":1872000,"durationMs":2400000,"app":"com.example"},
         "volume":0.42,"muted":false,"pointer":true,"pin":false}
        """
        guard case let .state(state)? = Wire.decode(frame) else { return XCTFail("expected state") }
        XCTAssertEqual(state.screen, "home")
        XCTAssertEqual(state.focusLabel, "Netflix")
        XCTAssertEqual(state.media?.title, "The Bear")
        XCTAssertEqual(state.media?.positionMs, 1_872_000)
        XCTAssertEqual(state.pointer, true)

        guard case let .apps(apps)? = Wire.decode("""
        {"t":"apps","apps":[{"pkg":"com.netflix.mediaclient","name":"Netflix","color":"#E50914","fav":true}]}
        """) else { return XCTFail("expected apps") }
        XCTAssertEqual(apps.count, 1)
        XCTAssertEqual(apps[0].package, "com.netflix.mediaclient")
        XCTAssertTrue(apps[0].favourite)
    }

    func testUnknownAndMalformedFramesAreIgnored() {
        XCTAssertNil(Wire.decode("{\"t\":\"teleport\"}"))
        XCTAssertNil(Wire.decode("not json"))
        XCTAssertNil(Wire.decode(""))
        XCTAssertNil(Wire.decode("{\"no\":\"type\"}"))
        // A known type with a missing token cannot be a welcome, and must not crash.
        XCTAssertNil(Wire.decode("{\"t\":\"welcome\",\"device\":\"x\"}"))
        // Extra keys are tolerated.
        if case .toast? = Wire.decode("{\"t\":\"toast\",\"text\":\"Volume 40%\",\"future\":1}") {} else {
            XCTFail("expected a toast")
        }
    }

    // MARK: - Helpers

    private func parse(_ frame: String) throws -> [String: Any] {
        let data = Data(frame.utf8)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
