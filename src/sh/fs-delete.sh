#!/bin/sh

# delete an existing file
BASE=`dirname $0`
source $BASE/common.sh

OBJID=$1
FILEID=$2
curl -u $USER:$PASS -i -X DELETE http://localhost:8080/dams/api/files/$OBJID/$FILEID
if [ $? != 0 ]; then
    exit 1
fi
echo
