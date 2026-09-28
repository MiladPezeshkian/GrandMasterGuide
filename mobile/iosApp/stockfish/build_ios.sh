#!/bin/bash
# Builds Stockfish (with its NNUE network embedded) plus the Zorix bridge into
# libzorixstockfish.a for iOS devices (arm64). Needs macOS with Xcode.
# Usage: build_ios.sh <output-dir>
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MOBILE="$(cd "$HERE/../.." && pwd)"
SRC="$MOBILE/stockfish/src"
NETS="$MOBILE/stockfish/nets"
OUT="${1:-$MOBILE/shared/build/stockfish-ios}"
LIB="$OUT/libzorixstockfish.a"

if [ -f "$LIB" ] && [ "$LIB" -nt "$HERE/zorix_stockfish.cpp" ] && [ "$LIB" -nt "$SRC/evaluate.h" ]; then
  echo "Stockfish for iOS is up to date: $LIB"
  exit 0
fi

# 1. The neural network (downloaded once, then cached in stockfish/nets).
NET="$(grep -o 'nn-[0-9a-f]\{12\}\.nnue' "$SRC/evaluate.h" | head -1)"
mkdir -p "$NETS"
if [ ! -f "$NETS/$NET" ]; then
  for url in "https://tests.stockfishchess.org/api/nn/$NET" \
             "https://github.com/official-stockfish/networks/raw/master/$NET" \
             "https://media.githubusercontent.com/media/official-stockfish/networks/master/$NET"; do
    echo "Downloading $NET from $url"
    if curl -fsSL --retry 3 -o "$NETS/$NET.part" "$url"; then
      if [ "nn-$(shasum -a 256 "$NETS/$NET.part" | cut -c1-12).nnue" = "$NET" ]; then
        mv "$NETS/$NET.part" "$NETS/$NET"; break
      fi
    fi
    rm -f "$NETS/$NET.part"
  done
fi
[ -f "$NETS/$NET" ] || { echo "error: could not get $NET"; exit 1; }

# 2. Compile. The .incbin directive embeds the network relative to the working directory.
WORK="$OUT/obj"
rm -rf "$WORK" && mkdir -p "$WORK"
ln -sf "$NETS/$NET" "$WORK/$NET"
SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
CXX="$(xcrun --sdk iphoneos --find clang++)"
FLAGS=(-arch arm64 -isysroot "$SDK" -miphoneos-version-min=15.0 -std=c++17 -O3 -funroll-loops
       -DNDEBUG -DIS_64BIT -DUSE_POPCNT -DUSE_NEON=8 -DARCH=ios-arm64 -Wno-everything)

SOURCES="$(sed -e ':a' -e '/\\$/N; s/\\\n//; ta' "$SRC/Makefile" | grep -E '^SRCS[[:space:]]*=' | head -1 | sed 's/^SRCS[[:space:]]*=//')"
cd "$WORK"
pids=()
for f in $SOURCES; do
  obj="$(echo "$f" | tr '/' '_' | sed 's/\.cpp$/.o/')"
  extra=""
  [ "$f" = "main.cpp" ] && extra="-Dmain=zorix_stockfish_main"
  "$CXX" "${FLAGS[@]}" $extra -c "$SRC/$f" -o "$obj" &
  pids+=($!)
  if [ "${#pids[@]}" -ge 8 ]; then wait "${pids[0]}"; pids=("${pids[@]:1}"); fi
done
"$CXX" "${FLAGS[@]}" -I"$HERE" -c "$HERE/zorix_stockfish.cpp" -o zorix_stockfish.o &
pids+=($!)
for p in "${pids[@]}"; do wait "$p"; done

# 3. Archive.
rm -f "$LIB"
xcrun libtool -static -o "$LIB" ./*.o
echo "Built $LIB ($(du -h "$LIB" | cut -f1))"
