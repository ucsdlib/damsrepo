#!/bin/sh

COUNT=3

. common.sh

URL="$FEDORA_BASE/objects/nextPID?numPIDs=$COUNT&format=xml"
echo "URL: $URL"
curl -v -X POST "$URL"
