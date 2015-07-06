#!/bin/sh

# upload a new file, from local staging
BASE=`dirname $0`
. $BASE/common.sh

OBJID=$1
FILEID=$2
FILE=$3
curl -u $USER:$PASS -i -L -X POST -F local=$FILE $URL/api/files/$OBJID/$FILEID?fs=openStack
if [ $? != 0 ]; then
	exit 1
fi
echo
