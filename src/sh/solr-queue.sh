#!/bin/sh

# bulk index records in solr
BASE=`dirname $0`
source $BASE/common.sh

curl -u $USER:$PASS -X POST -F id=bb01010101 http://localhost:8080/dams/api/queue
if [ $? != 0 ]; then
    exit 1
fi
echo
