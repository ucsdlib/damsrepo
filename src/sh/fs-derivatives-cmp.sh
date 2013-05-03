#!/bin/sh

# upload a new file
BASE=`dirname $0`
source $BASE/common.sh

OBJID=$1
CMPID=$2
FILEID=$3
curl -u $USER:$PASS -i -L -X POST "http://localhost:8080/dams/api/files/$OBJID/$CMPID/$FILEID/derivatives?size=2&size=3&size=4&size=5&size=6&size=7"
if [ $? != 0 ]; then
    exit 1
fi
echo
