#!/bin/sh

# update a metadata record
BASE=`dirname $0`
source $BASE/common.sh

OBJID=bb52572546
DISS=$BASE/../sample/object/diss.rdf.xml

curl -u $USER:$PASS -X PUT -F file=@$DISS http://localhost:8080/dams/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
