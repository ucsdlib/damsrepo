#!/bin/sh

# upload a new file, from local staging

OBJID=$1
CMPID=$2
FILEID=$3
FILE=$4
curl -i -L -X POST -F local=$FILE http://localhost:8080/dams/api/files/$OBJID/$CMPID/$FILEID
if [ $? != 0 ]; then
	exit 1
fi
echo
