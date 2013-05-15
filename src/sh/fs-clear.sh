#!/bin/sh

# delete all triples from a triplestore

BASE=`dirname $0`
source $BASE/common.sh

FS=$1
if [ ! "$FS" ]; then
	echo "usage: $0 fsname"
	exit 1
fi
FS_DIR=`grep "fs.$FS.baseDir" $PROPS | cut -d= -f2 | tr -d ' '`
if [ ! "$FS_DIR" ]; then
	echo "Error: Base directory for $FS not found"
	exit 2
elif [ "${FS_DIR:0:1}" != "/" ]; then
	FS_DIR="$BASE/../../$FS_DIR"
fi
echo "clearing filestore ($FS): $FS_DIR"
rm -rf "$FS_DIR"/*
