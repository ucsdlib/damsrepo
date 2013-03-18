#!/bin/sh

# upload a new file

OBJID=$1
FILEID=$2
if [ "$3" ]; then
	FS="fs=$3"
fi
curl -i -L -X PUT "http://localhost:8080/dams/api/files/$OBJID/$FILEID/derivatives?size=2&size=3&size=4&size=5&size=6&size=7&$FS"
if [ $? != 0 ]; then
    exit 1
fi
echo
