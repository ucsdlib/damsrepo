#!/bin/sh

# upload a new file

OBJID=$1
CMPID=$2
FILEID=$3
SIZES="-F size=2 -F size=3 -F size=4 -F size=5"
curl -i -L -X POST $SIZES http://localhost:8080/dams/api/files/$OBJID/$CMPID/$FILEID/derivatives
echo
