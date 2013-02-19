#!/bin/sh

# create a metadata record

OBJID=$1
DISS=$2

curl -X POST -F file=@$DISS http://localhost:8080/dams/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
