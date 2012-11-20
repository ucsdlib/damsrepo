#!/bin/sh

ID=aa00000006
DS=_1_3.txt
METHOD=POST
FILE=new_datastream.txt

. common.sh

URL="$FEDORA_BASE/objects/$ID/datastreams/$DS"
echo "URL: $URL"
curl -v -F file=@$FILE -X $METHOD "$URL"
