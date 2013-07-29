#!/bin/sh

# generate a new identifier

BASE=`dirname $0`
source $BASE/common.sh

if [ "$1" ]; then
	COUNT="?count=$1"
fi
curl -X POST -u $USER:$PASS $URL/api/next_id$COUNT
if [ $? != 0 ]; then
    exit 1
fi
echo
