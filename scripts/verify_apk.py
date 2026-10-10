#!/usr/bin/env python3
"""Verify the packaged model and ELF LOAD alignment, without any extra package."""
import hashlib
import struct
import sys
import zipfile

EXPECTED = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"

def check(path):
    failures = []
    with zipfile.ZipFile(path) as apk:
        model = apk.read("assets/face_landmarker.task")
        if hashlib.sha256(model).hexdigest() != EXPECTED:
            failures.append("Model SHA-256 mismatch")
        libraries = [n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")]
        if not libraries:
            failures.append("No native libraries packaged")
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
    print("ELF 16 KB alignment OK. Runtime hardware validation is still required.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_apk.py path/to/app.apk")
    check(sys.argv[1])
