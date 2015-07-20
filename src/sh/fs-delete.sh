#!/bin/sh

# delete an existing file
BASE=`dirname $0`
. $BASE/common.sh

OBJID=$1
FILEID=$2
FS=$3
if [ "$FS" ]; then
	OPT="?fs=$FS"
fi
curl -u $USER:$PASS -i -X DELETE $URL/api/files/$OBJID/$FILEID$OPT
if [ $? != 0 ]; then
    exit 1
fi
echo
