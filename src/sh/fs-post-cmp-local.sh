#!/bin/sh

# upload a new file, from local staging
BASE=`dirname $0`
source $BASE/common.sh

OBJID=$1
CMPID=$2
FILEID=$3
FILE=$4
curl -u $USER:$PASS -i -L -X POST -F local=$FILE $URL/api/files/$OBJID/$CMPID/$FILEID
if [ $? != 0 ]; then
	exit 1
fi
echo
