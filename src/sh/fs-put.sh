#!/bin/sh

# update an existing file
BASE=`dirname $0`
source $BASE/common.sh

OBJID=$1
FILEID=$2
FILE=$3
USE=$4
if [ "$USE" ]; then
    OPT="?use=$USE"
	ARG="-F use=$USE"
fi
curl -u $USER:$PASS -i -X PUT -d @$FILE $URL/api/files/$OBJID/$FILEID$OPT
if [ $? != 0 ]; then
    exit 1
fi
echo
