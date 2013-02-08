#!/bin/sh

# upload a new file, from local staging

OBJID=$1
FILEID=$2
FILE=$3
curl -i -L -X POST -F local=$FILE http://localhost:8080/dams/api/files/$OBJID/$FILEID?fs=openStack
if [ $? != 0 ]; then
	exit 1
fi
echo
