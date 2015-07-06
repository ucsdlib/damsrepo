#!/bin/sh

# update a metadata record

OBJID=$1
FILE=$2
if [ "$3" ]; then
	TS="?ts=$3"
fi

BASE=`dirname $0`
. $BASE/common.sh

curl -k -u $USER:$PASS -X PUT -F file=@$FILE $URL/api/objects/$OBJID$TS
if [ $? != 0 ]; then
    exit 1
fi
echo
