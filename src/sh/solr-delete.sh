#!/bin/sh

# delete a record from solr
BASE=`dirname $0`
source $BASE/common.sh

curl -u $USER:$PASS -X DELETE $URL/api/objects/bb01010101/index
if [ $? != 0 ]; then
    exit 1
fi
echo
