#!/bin/sh

# delete an existing file

OBJID=$1
FILEID=$2
curl -i -X DELETE http://localhost:8080/dams/api/files/$OBJID/$FILEID
if [ $? != 0 ]; then
    exit 1
fi
echo
