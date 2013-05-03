#!/bin/sh

# upload a new file
BASE=`dirname $0`
source $BASE/common.sh

OBJID=$1
FILEID=$2
FILE=$3
DIR=`dirname $FILE`
curl -u $USER:$PASS -i -L -X POST -F sourcePath="$DIR" -F fs=alt -F file=@$FILE http://localhost:8080/dams/api/files/$OBJID/$FILEID
if [ $? != 0 ]; then
    exit 1
fi
echo
