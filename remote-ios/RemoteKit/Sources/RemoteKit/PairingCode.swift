import Foundation

/// The six-digit code the TV shows, as the phone sees it.
///
/// The TV is the authority: it issues codes, expires them after five minutes and blocks an address
/// after five wrong tries. This type only normalises what the user types and formats it the way the
/// TV displays it, so the two screens agree at a glance.
public enum PairingCode {
    public static let length = 6

    /// Keeps digits only, caps the length, and never reorders them.
    public static func sanitize(_ input: String) -> String {
        let digits = input.filter(\.isNumber)
        return String(digits.prefix(length))
    }

    /// `"123456"` → `"123 456"`. Grouping by three is easier to read across a room than six digits
    /// in a row, and matches the TV's own presentation.
    public static func display(_ input: String) -> String {
        let digits = sanitize(input)
        guard digits.count > 3 else { return digits }
        let split = digits.index(digits.startIndex, offsetBy: 3)
        return "\(digits[..<split]) \(digits[split...])"
    }

    public static func isComplete(_ input: String) -> Bool {
        sanitize(input).count == length
    }

    /// What to say under the field: nothing while it is plausibly incomplete, a nudge once it is.
    public static func hint(for input: String, wrongAttempts: Int) -> String? {
        if wrongAttempts >= 5 {
            return "Too many attempts. Wait a few minutes, then ask the TV for a new code."
        }
        if wrongAttempts > 0 && isComplete(input) {
            return "That code was not accepted. Check the TV — the code changes every 5 minutes."
        }
        if !input.isEmpty && !isComplete(input) {
            return "The code is six digits, shown on the TV under Settings › Phone remote."
        }
        return nil
    }
}
