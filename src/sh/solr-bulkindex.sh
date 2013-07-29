#!/bin/sh

# bulk index records in solr
BASE=`dirname $0`
source $BASE/common.sh

curl -u $USER:$PASS -X POST -F id=bb01010101 $URL/api/index
if [ $? != 0 ]; then
    exit 1
fi
echo
