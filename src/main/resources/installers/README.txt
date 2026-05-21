7-Zip installer bundling
========================

To enable the in-app "Install 7-Zip" prompt, drop the OFFICIAL signed installer
from https://www.7-zip.org/download.html into this directory as:

    7z-installer.exe

The installer must be the 64-bit Windows installer (.exe). The app will copy it
to a temp file at runtime and run it with the silent flag /S.

If this file is absent, the build will still succeed and the app will fall back
to telling the user where to download 7-Zip manually.

The 7-Zip installer is NOT committed to git. Add it locally before building the
1.1 release if you want bundled-installer behavior.
