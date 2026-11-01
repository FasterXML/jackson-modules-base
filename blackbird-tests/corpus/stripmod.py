"""Copy a jar without any module-info.class entries (base or versioned)."""
import sys
import zipfile

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename.endswith("module-info.class"):
            continue
        zout.writestr(item, zin.read(item.filename))
