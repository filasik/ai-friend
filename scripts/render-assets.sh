#!/usr/bin/env bash
# Vyrenderuje docs/assets/*.svg do PNG (icon 512x512, banner 1200x300).
set -euo pipefail
cd "$(dirname "$0")/.."
render() { # svg png width height
  if command -v rsvg-convert >/dev/null; then rsvg-convert -w "$3" -h "$4" "$1" -o "$2"
  elif command -v magick >/dev/null; then magick -background none -density 300 "$1" -resize "${3}x${4}" "$2"
  else echo "Chybí rsvg-convert nebo ImageMagick (brew install librsvg)"; exit 1; fi
  echo "OK $2"
}
render docs/assets/icon.svg   docs/assets/icon.png   512 512
render docs/assets/banner.svg docs/assets/banner.png 1200 300
