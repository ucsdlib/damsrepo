#!/bin/sh

ID=aa00000006

. common.sh

URL="$FEDORA_BASE/objects/$ID"
echo "URL: $URL"
curl -v -F file=@newobject_file.rdf.xml -X POST "$URL"
