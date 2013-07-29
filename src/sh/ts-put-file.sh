#!/bin/sh

# load metadata from file
BASE=`dirname $0`
source $BASE/common.sh

OBJID=$1
RDF=$2

curl -u $USER:$PASS -X PUT -F mode=add -F file=@$RDF $URL/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
