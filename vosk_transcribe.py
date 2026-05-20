#!/usr/bin/env python3
"""Vosk speech-to-text transcription script for TaskBot."""
import sys
import json
import wave

try:
    from vosk import Model, KaldiRecognizer
except ImportError:
    print("ERROR: vosk module not installed", file=sys.stderr)
    sys.exit(1)

MODEL_PATH = "/app/vosk-model"


def transcribe(wav_path: str) -> str:
    model = Model(MODEL_PATH)
    wf = wave.open(wav_path, "rb")
    rec = KaldiRecognizer(model, wf.getframerate())

    parts = []
    while True:
        data = wf.readframes(4000)
        if len(data) == 0:
            break
        if rec.AcceptWaveform(data):
            res = json.loads(rec.Result())
            text = res.get("text", "")
            if text:
                parts.append(text)

    final = json.loads(rec.FinalResult())
    final_text = final.get("text", "")
    if final_text:
        parts.append(final_text)

    wf.close()
    return " ".join(parts)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: vosk_transcribe.py <wav_file>", file=sys.stderr)
        sys.exit(1)

    result = transcribe(sys.argv[1])
    print(result)