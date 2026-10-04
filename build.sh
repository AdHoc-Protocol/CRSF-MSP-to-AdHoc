#!/usr/bin/env bash
# Compiles the converter and runs it over samples/ into AdHoc/. Java 17+ required.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out AdHoc
javac -encoding UTF-8 --release 17 -d out src/org/unirail/adhoc/*.java src/org/unirail/*.java
java -Dfile.encoding=UTF-8 -cp out org.unirail.CHeaders2AdHoc samples AdHoc
