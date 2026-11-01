import SwiftUI
import Speech
import AVFoundation

/// Voice search, transcribed on the phone.
///
/// Nothing but text is ever sent to the TV — no audio leaves the device, which is both a privacy
/// feature and the reason voice works over a poor Wi-Fi link.
final class VoiceTranscriber: ObservableObject {
    @Published private(set) var transcript = ""
    @Published private(set) var listening = false
    @Published private(set) var failure: String?

    private let recognizer = SFSpeechRecognizer(locale: Locale.current)
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func start() {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else {
                    self.failure = "Speech recognition is not allowed. Enable it in Settings › Couch Remote."
                    return
                }
                self.record()
            }
        }
    }

    private func record() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            self.request = request

            let input = engine.inputNode
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
            listening = true
            failure = nil

            task = recognizer?.recognitionTask(with: request) { [weak self] result, error in
                guard let self else { return }
                if let result {
                    DispatchQueue.main.async { self.transcript = result.bestTranscription.formattedString }
                }
                if error != nil || result?.isFinal == true {
                    DispatchQueue.main.async { self.stopListening() }
                }
            }
        } catch {
            failure = "The microphone could not start."
            stopListening()
        }
    }

    func stopListening() {
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        request = nil
        task?.cancel()
        task = nil
        listening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

/// The sheet: hold to talk, or tap once and speak — both end with a transcript on the TV.
struct VoiceSheet: View {
    let onResult: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var transcriber = VoiceTranscriber()

    var body: some View {
        ZStack {
            Theme.panel.ignoresSafeArea()

            VStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Theme.phosphor.opacity(transcriber.listening ? 0.22 : 0.08))
                        .frame(width: 84, height: 84)
                    Image(systemName: transcriber.listening ? "waveform" : "mic.fill")
                        .font(.system(size: 30, weight: .medium))
                        .foregroundColor(transcriber.listening ? Theme.phosphor : Theme.dim)
                }

                Text(transcriber.transcript.isEmpty ? "Speak your search" : transcriber.transcript)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(transcriber.transcript.isEmpty ? Theme.faint : Theme.ink)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                if let failure = transcriber.failure {
                    Text(failure)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.warning)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 10) {
                    Button("Cancel") {
                        transcriber.stopListening()
                        dismiss()
                    }
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Theme.dim)

                    Button {
                        if transcriber.transcript.isEmpty { return }
                        onResult(transcriber.transcript)
                        transcriber.stopListening()
                        Haptics.press()
                        dismiss()
                    } label: {
                        Text("Search on TV")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(transcriber.transcript.isEmpty ? Theme.faint : Theme.canvas)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 11)
                            .background(
                                Capsule().fill(transcriber.transcript.isEmpty ? Color.white.opacity(0.06) : Theme.phosphor)
                            )
                    }
                    .disabled(transcriber.transcript.isEmpty)
                }
            }
            .padding(22)
        }
        .preferredColorScheme(.dark)
        .onAppear { transcriber.start() }
        .onDisappear { transcriber.stopListening() }
    }
}
