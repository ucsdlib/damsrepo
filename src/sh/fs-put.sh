#!/bin/sh

# update an existing file

OBJID=$1
FILEID=$2
FILE=$3
curl -i -X PUT -d @$FILE http://localhost:8080/dams/api/files/$OBJID/$FILEID
echo
