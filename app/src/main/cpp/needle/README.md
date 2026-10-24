# Cactus needle engine (prebuilt)

The speech engine that runs Whistle (subtitles). Apache 2.0 (`LICENSE`), by Cactus Compute.
Copied unchanged from https://huggingface.co/Cactus-Compute/needle3 at commit `d6c7d6a2e9655505e0f8e23339356dd1705900eb`:
`android-arm64/` → `arm64-v8a/`, `android-armv7/` → `armeabi-v7a/`, `needle.h` → `include/`.
Source: https://github.com/cactus-compute/needle (F-Droid builds will need it built from source).
Checked 2026-10-24: no network calls (no socket/connect/getaddrinfo, no URLs); the Python
package's usage counting isn't in the engine.
