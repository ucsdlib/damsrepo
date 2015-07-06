#!/bin/sh

# bulk index records in solr
BASE=`dirname $0`
. $BASE/common.sh

curl -u $USER:$PASS -X POST -F id=bb01010101 $URL/api/queue
if [ $? != 0 ]; then
    exit 1
fi
echo
