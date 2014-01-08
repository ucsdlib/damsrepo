#!/bin/sh

# index a record in solr
BASE=`dirname $0`
source $BASE/common.sh

echo "curl -u $USER:$PASS -X POST $URL/api/objects/$1/index"
curl -u $USER:$PASS -X POST $URL/api/objects/$1/index
if [ $? != 0 ]; then
    exit 1
fi
echo
