#!/bin/sh

# update a metadata record

OBJID=bb52572546
DISS=src/sample/object/diss.rdf.xml

curl -X PUT -F file=@$DISS http://localhost:8080/dams/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
