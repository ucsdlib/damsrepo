#!/bin/sh

# upload a new file

OBJID=$1
FILEID=$2
FILE=$3
DIR=`dirname $FILE`
curl -i -L -X POST -F sourcePath="$DIR" -F file=@$FILE http://localhost:8080/dams/api/files/$OBJID/$FILEID
if [ $? != 0 ]; then
    exit 1
fi
echo
