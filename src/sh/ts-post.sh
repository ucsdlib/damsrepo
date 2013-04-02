#!/bin/sh

# create a metadata record

OBJID=$1
DISS=$2

BASE=`dirname $0`
source $BASE/common.sh

curl -k -u $USER:$PASS -X POST -F file=@$DISS https://localhost:8443/dams/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
