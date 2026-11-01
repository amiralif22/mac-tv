import XCTest
@testable import RemoteKit

final class PairingCodeTests: XCTestCase {

    func testSanitizeKeepsSixDigits() {
        XCTAssertEqual(PairingCode.sanitize("12 34-56"), "123456")
        XCTAssertEqual(PairingCode.sanitize("123456789"), "123456")
        XCTAssertEqual(PairingCode.sanitize("abc"), "")
    }

    func testDisplayGroupsByThree() {
        XCTAssertEqual(PairingCode.display("123456"), "123 456")
        XCTAssertEqual(PairingCode.display("12"), "12")
        XCTAssertEqual(PairingCode.display("1234"), "123 4")
    }

    func testCompletenessAndHints() {
        XCTAssertTrue(PairingCode.isComplete("123456"))
        XCTAssertFalse(PairingCode.isComplete("12345"))
        XCTAssertNil(PairingCode.hint(for: "", wrongAttempts: 0))
        XCTAssertNotNil(PairingCode.hint(for: "123", wrongAttempts: 0))
        XCTAssertNotNil(PairingCode.hint(for: "123456", wrongAttempts: 1))
        XCTAssertEqual(
            PairingCode.hint(for: "123456", wrongAttempts: 5),
            "Too many attempts. Wait a few minutes, then ask the TV for a new code."
        )
    }
}
