#!/bin/sh

# delete an existing object

OBJID=$1
curl -i -X DELETE http://localhost:8080/dams/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
