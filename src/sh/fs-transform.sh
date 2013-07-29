#!/bin/sh

# transform and save
BASE=`dirname $0`
source $BASE/common.sh

curl -u $USER:$PASS -X POST "$URL/api/objects/bb01010101/transform?xsl=solrindexer.xsl&recursive=true&dest=3.xml"
if [ $? != 0 ]; then
    exit 1
fi
echo
