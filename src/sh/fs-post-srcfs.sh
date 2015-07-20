#!/bin/sh

# upload a new file, from local staging
BASE=`dirname $0`
. $BASE/common.sh

OBJID=$1
FILEID=$2
SRC=$3
FS=$4
if [ "$FS" ]; then
	FSOPT="-F fs=$FS"
fi
curl -u $USER:$PASS -i -L -X POST $FSOPT -F srcfs=$SRC $URL/api/files/$OBJID/$CMPID/$FILEID
if [ $? != 0 ]; then
	exit 1
fi
echo
