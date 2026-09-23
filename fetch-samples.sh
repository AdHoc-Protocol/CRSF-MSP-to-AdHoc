#!/usr/bin/env bash
# Downloads the upstream sources the converter reads:
#   samples/crsf/crsf_protocol.h        ExpressLRS reference header (frame types, addresses, packed payload structs)
#   samples/crsf.wiki/*.md              crsf-wg protocol wiki (frame table with direction/description, per-frame pages)
#   samples/msp/msp_protocol*.h         Betaflight MSP command ids (v1 + v2)
#   samples/msp/msp.c                   Betaflight MSP handlers - reference only, the hand-maintained payload table
#                                       in the converter was transcribed from it
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p samples/crsf samples/msp

curl -sSf -o samples/crsf/crsf_protocol.h \
    https://raw.githubusercontent.com/ExpressLRS/ExpressLRS/master/src/include/crsf_protocol.h

for f in msp_protocol.h msp_protocol_v2_common.h msp_protocol_v2_betaflight.h msp.c; do
    curl -sSf -o "samples/msp/$f" "https://raw.githubusercontent.com/betaflight/betaflight/master/src/main/msp/$f"
done

rm -rf samples/crsf.wiki
git clone -q --depth 1 https://github.com/crsf-wg/crsf.wiki.git samples/crsf.wiki
rm -rf samples/crsf.wiki/.git

echo "samples refreshed"
