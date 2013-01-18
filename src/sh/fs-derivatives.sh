#!/bin/sh

# upload a new file

OBJID=$1
FILEID=$2
SIZES="-F size=2 -F size=3 -F size=4 -F size=5"
curl -i -L -X POST $SIZES http://localhost:8080/dams/api/files/$OBJID/$FILEID/derivatives
if [ $? != 0 ]; then
    exit 1
fi
echo
