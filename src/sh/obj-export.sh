#!/bin/sh

# export object files and metadata to disk

BASE=`dirname $0`
. $BASE/common.sh

TS=dams
ES=events
CLASS=edu.ucsd.library.dams.commands.ObjectExport

java -cp $CP $CLASS $PROPS $TS $ES $*
if [ $? != 0 ]; then
    exit 1
fi
