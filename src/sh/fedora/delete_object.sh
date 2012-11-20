#!/bin/sh

ID=aa00000006
METHOD=DELETE

. common.sh

URL="$FEDORA_BASE/objects/$ID"
echo "URL: $URL"
curl -v -X $METHOD "$URL"
