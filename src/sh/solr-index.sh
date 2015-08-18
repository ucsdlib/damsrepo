#!/bin/sh

# index a record in solr
BASE=`dirname $0`
. $BASE/common.sh

echo "curl -u $USER:$PASS -X POST $URL/api/objects/$1/index"
if [ "$2" ]; then
	OPTS="?priority=$2"
fi
curl -u $USER:$PASS -X POST $URL/api/objects/$1/index$OPTS
if [ $? != 0 ]; then
    exit 1
fi
echo
