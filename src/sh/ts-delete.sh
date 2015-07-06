#!/bin/sh

# delete an existing object

OBJID=$1
if [ "$2" ]; then
	TS="?ts=$2"
fi

BASE=`dirname $0`
. $BASE/common.sh

curl -k -u $USER:$PASS -i -X DELETE $URL/api/objects/$OBJID$TS
if [ $? != 0 ]; then
    exit 1
fi
echo
