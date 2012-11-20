#!/bin/sh

ID=aa00000003

. common.sh

URL="$FEDORA_BASE/objects/$ID"
echo "URL: $URL"
curl -v -X POST "$URL"
