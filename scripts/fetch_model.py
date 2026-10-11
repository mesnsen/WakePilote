#!/usr/bin/env python3
"""Build-time only. Never called by the Android application."""
import hashlib
from pathlib import Path
import urllib.request

URL = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
SHA256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/face_landmarker.task"

def main():
    if DEST.exists() and hashlib.sha256(DEST.read_bytes()).hexdigest() == SHA256:
        print("Pinned face model already verified.")
        return
    with urllib.request.urlopen(URL, timeout=90) as response:
        payload = response.read(16 * 1024 * 1024)
    if hashlib.sha256(payload).hexdigest() != SHA256:
        raise SystemExit("Model hash mismatch. Refusing to package unverified bytes.")
    DEST.parent.mkdir(parents=True, exist_ok=True)
    temporary = DEST.with_suffix(".download")
    temporary.write_bytes(payload)
    temporary.replace(DEST)
    print(f"Pinned model verified: {len(payload)} bytes. The APK will work offline.")

if __name__ == "__main__":
    main()
