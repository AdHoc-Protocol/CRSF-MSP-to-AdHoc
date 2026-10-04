#!/usr/bin/env bash
# Downloads the upstream sources the converter reads:
#   samples/crsf/crsf_protocol.h        ExpressLRS reference header (frame types, addresses, packed payload structs)
#   samples/crsf.wiki/*.md              crsf-wg protocol wiki (frame table with direction/description, per-frame pages)
#   samples/msp/msp_protocol*.h         Betaflight MSP command ids (v1 + v2)
#   samples/msp/msp.c                   Betaflight MSP handlers - reference only, the hand-maintained payload table
#                                       in the converter was transcribed from it
# and writes samples/sources.txt, the page of the original of each.
set -euo pipefail
cd "$(dirname "$0")"
ELRS="https://raw.githubusercontent.com/ExpressLRS/ExpressLRS/master/src/include"
BF="https://raw.githubusercontent.com/betaflight/betaflight/master/src/main/msp"
WIKI="https://github.com/crsf-wg/crsf/wiki/"    # the pages of the wiki that crsf.wiki.git holds
# <path in samples/> <url>
FILES="
crsf/crsf_protocol.h                $ELRS/crsf_protocol.h
msp/msp_protocol.h                  $BF/msp_protocol.h
msp/msp_protocol_v2_common.h        $BF/msp_protocol_v2_common.h
msp/msp_protocol_v2_betaflight.h    $BF/msp_protocol_v2_betaflight.h
msp/msp.c                           $BF/msp.c
"
# The page of a file in its repository, for a reader: raw.githubusercontent.com gives the bare text.
page() {
    case "$1" in
        https://raw.githubusercontent.com/*)
            local p="${1#https://raw.githubusercontent.com/}"
            local owner="${p%%/*}"; p="${p#*/}"
            local repo="${p%%/*}"; p="${p#*/}"
            echo "https://github.com/$owner/$repo/blob/$p" ;;
        *) echo "$1" ;;
    esac
}
# Percent-encodes a page name for a URL (two wiki pages have a U+2010 hyphen in their names).
urlencode() {
    local LC_ALL=C s="$1" out="" c i
    for ((i = 0; i < ${#s}; i++)); do
        c="${s:i:1}"
        case "$c" in
            [A-Za-z0-9._~/-]) out+="$c" ;;
            *) out+="$(printf '%%%02X' "'$c")" ;;
        esac
    done
    echo "$out"
}
# A line of sources.txt, the path padded by characters rather than bytes (UTF-8 continuation bytes do not count).
row() {
    local LC_ALL=C n="$1" cont
    cont=${n//[^$'\x80'-$'\xbf']/}
    printf '%s%*s %s\n' "$n" $((53 - ${#n} + ${#cont})) '' "$2"
}
mkdir -p samples/crsf samples/msp

while read -r n url; do
    [ -z "$n" ] && continue
    curl -sSf -o "samples/$n" "$url"
done <<< "$FILES"

rm -rf samples/crsf.wiki
git clone -q --depth 1 https://github.com/crsf-wg/crsf.wiki.git samples/crsf.wiki
rm -rf samples/crsf.wiki/.git

# A wiki page is its file name without .md; _Sidebar.md and the like are parts of every page, not pages.
{
    echo "# Where every sample comes from: <path in samples/> <page of the original>. Written by fetch-samples.sh;"
    echo "# the converter links these pages in the headers of the descriptions. A path ending with / covers its folder."
    while read -r n url; do
        [ -z "$n" ] && continue
        row "$n" "$(page "$url")"
    done <<< "$FILES"
    row "crsf.wiki/" "$WIKI"
    for f in samples/crsf.wiki/*.md; do
        p="${f##*/}"
        case "$p" in _*) continue ;; esac
        row "${f#samples/}" "$WIKI$(urlencode "${p%.md}")"
    done
} > samples/sources.txt

echo "samples refreshed"
