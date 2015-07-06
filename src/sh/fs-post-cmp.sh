#!/bin/sh

# upload a new file
BASE=`dirname $0`
. $BASE/common.sh

OBJID=$1
CMPID=$2
FILEID=$3
FILE=$4
DIR=`dirname $FILE`
curl -u $USER:$PASS -i -L -X POST -F sourcePath="$DIR" -F file=@$FILE $URL/api/files/$OBJID/$CMPID/$FILEID
if [ $? != 0 ]; then
    exit 1
fi
echo
