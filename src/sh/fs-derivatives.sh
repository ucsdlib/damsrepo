#!/bin/sh

# upload a new file

OBJID=$1
FILEID=$2
curl -i -L -X POST "http://localhost:8080/dams/api/files/$OBJID/$FILEID/derivatives?size=2&size=3&size=4&size=5&size=6&size=7"
if [ $? != 0 ]; then
    exit 1
fi
echo
