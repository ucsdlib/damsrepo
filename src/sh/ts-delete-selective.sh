#!/bin/sh

# delete single predicates only
BASE=`dirname $0`
. $BASE/common.sh

OBJID=$1
curl -u $USER:$PASS -i -X DELETE "$URL/api/objects/$OBJID/selective?predicate=dams:relationship&predicate=dams:unit"
if [ $? != 0 ]; then
    exit 1
fi
echo
