#!/usr/bin/env python3
"""
zipalign, in Python.

Android will not install an APK whose resources.arsc is compressed or unaligned once the
app targets API 30 or later, and the platform maps stored entries straight out of the file,
so every uncompressed entry has to start on a 4-byte boundary. The SDK ships a `zipalign`
binary for this; this is the same job without the SDK.

Padding goes in the local header's extra field, which readers skip by length, so the entry
data lands where we want it. Deflated entries are copied as-is: nothing maps those.

    zipalign.py <in.apk> <out.apk>
    zipalign.py --check <apk>
"""

import shutil
import struct
import sys
import zipfile

ALIGNMENT = 4
STORED_UNCOMPRESSED = ("resources.arsc",)   # must additionally never be deflated


def _local_header_length(name_bytes, extra_len):
    return 30 + len(name_bytes) + extra_len


def align(src_path, dst_path):
    with zipfile.ZipFile(src_path, "r") as src, \
            zipfile.ZipFile(dst_path, "w", allowZip64=False) as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            name_bytes = info.filename.encode("utf-8")

            out = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            out.compress_type = info.compress_type
            out.external_attr = info.external_attr
            out.internal_attr = info.internal_attr
            out.create_system = info.create_system

            if info.filename in STORED_UNCOMPRESSED:
                out.compress_type = zipfile.ZIP_STORED

            if out.compress_type == zipfile.ZIP_STORED:
                # Where the data would land with no padding at all.
                offset = dst.fp.tell() + _local_header_length(name_bytes, 0)
                padding = (ALIGNMENT - (offset % ALIGNMENT)) % ALIGNMENT
                out.extra = b"\x00" * padding

            dst.writestr(out, data)

    return check(dst_path)


def check(path):
    """Report every stored entry that does not begin on a 4-byte boundary."""
    bad = []
    with open(path, "rb") as f, zipfile.ZipFile(path, "r") as z:
        for info in z.infolist():
            if info.compress_type != zipfile.ZIP_STORED:
                continue
            f.seek(info.header_offset)
            header = f.read(30)
            name_len, extra_len = struct.unpack("<HH", header[26:30])
            data_offset = info.header_offset + 30 + name_len + extra_len
            if data_offset % ALIGNMENT:
                bad.append((info.filename, data_offset))
    return bad


def main(argv):
    if len(argv) == 3 and argv[1] == "--check":
        bad = check(argv[2])
    elif len(argv) == 3:
        if argv[1] == argv[2]:
            print("input and output must differ", file=sys.stderr)
            return 2
        bad = align(argv[1], argv[2])
    else:
        print(__doc__.strip(), file=sys.stderr)
        return 2

    for name, offset in bad:
        print("unaligned: %s at %d" % (name, offset), file=sys.stderr)
    if bad:
        return 1
    print("all stored entries are %d-byte aligned" % ALIGNMENT)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
