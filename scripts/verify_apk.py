#!/usr/bin/env python3
"""Verify the model, privacy invariants and ELF LOAD alignment (Android SDK required)."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import zipfile

EXPECTED = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"

def check(path):
    failures = []
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    properties = Path("local.properties")
    if not sdk and properties.exists():
        sdk = next((s.split("=", 1)[1] for s in properties.read_text().splitlines() if s.startswith("sdk.dir=")), None)
    aapt = str(Path(sdk) / "build-tools/35.0.0/aapt") if sdk else shutil.which("aapt")
    if not aapt:
        raise SystemExit("Android SDK aapt required: set ANDROID_HOME")
    permissions = subprocess.check_output([aapt, "dump", "permissions", path], text=True)
    forbidden = {"INTERNET", "ACCESS_NETWORK_STATE", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"}
    for permission in forbidden:
        if re.search(r"android\.permission\." + permission + r"['\s]", permissions):
            failures.append(f"Forbidden permission: {permission}")
    with zipfile.ZipFile(path) as apk:
        model = apk.read("assets/face_landmarker.task")
        if hashlib.sha256(model).hexdigest() != EXPECTED:
            failures.append("Model SHA-256 mismatch")
        for asset in ("assets/mediapipe-license.txt", "assets/third-party-notices.txt"):
            if asset not in apk.namelist():
                failures.append(f"Missing license notice: {asset}")
        for name in apk.namelist():
            if name.endswith(".dex"):
                dex = apk.read(name)
                for descriptor in (b"Lcom/google/android/datatransport/", b"Lcom/google/mediapipe/tasks/core/logging/RemoteLoggingClient;",
                                   b"Lcom/google/mediapipe/tasks/core/logging/TasksStatsProtoLogger;"):
                    if descriptor in dex:
                        failures.append(f"Telemetry bytecode in {name}: {descriptor.decode()}")
        libraries = [n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")]
        if not libraries:
            failures.append("No native libraries packaged")
        abis = {n.split("/")[1] for n in libraries}
        if abis != {"arm64-v8a"}:
            failures.append(f"Unexpected/incomplete ABI set: {sorted(abis)}")
        if "lib/arm64-v8a/libmediapipe_tasks_vision_jni.so" not in libraries:
            failures.append("Missing MediaPipe ARM64 runtime")
        for name in libraries:
            data = apk.read(name)
            if data[:4] != b"\x7fELF":
                failures.append(f"{name}: not ELF")
                continue
            endian = "<" if data[5] == 1 else ">"
            if data[4] == 2:
                phoff = struct.unpack_from(endian + "Q", data, 32)[0]
                size, count = struct.unpack_from(endian + "HH", data, 54)
                align_offset, align_type = 48, "Q"
            else:
                phoff = struct.unpack_from(endian + "I", data, 28)[0]
                size, count = struct.unpack_from(endian + "HH", data, 42)
                align_offset, align_type = 28, "I"
            for i in range(count):
                offset = phoff + size * i
                if struct.unpack_from(endian + "I", data, offset)[0] == 1:
                    alignment = struct.unpack_from(endian + align_type, data, offset + align_offset)[0]
                    if alignment < 16384:
                        failures.append(f"{name}: LOAD alignment {alignment} < 16384")
        print(f"Model verified; {len(libraries)} native libraries inspected.")
    if failures:
        raise SystemExit("\n".join(failures))
    print("Privacy invariants and ELF 16 KB alignment OK. Runtime hardware validation is still required.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_apk.py path/to/app.apk")
    check(sys.argv[1])
