#!/bin/sh

ID=aa00000006
DS=DAMS-RDF

. common.sh

URL="$FEDORA_BASE/objects/$ID/datastreams/$DS"
echo "URL: $URL"
curl -v -F file=@update_object2.rdf.xml -X PUT "$URL"
