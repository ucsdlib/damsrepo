#!/bin/sh

# upload a new file

OBJID=$1
FILEID=$2
FILE=$3
curl -i -L -X POST -F file=@$FILE http://localhost:8080/dams/api/files/$OBJID/$FILEID
echo