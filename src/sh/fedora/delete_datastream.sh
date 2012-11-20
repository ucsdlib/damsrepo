#!/bin/sh

ID=aa00000006
DS=_1_3.txt
METHOD=DELETE

. common.sh

URL="$FEDORA_BASE/objects/$ID/datastreams/$DS"
echo "URL: $URL"
curl -v -X $METHOD "$URL"
