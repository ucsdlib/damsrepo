#!/bin/sh

# clear triplestore and load sample data

BASE=`dirname $0`

TS=$1
ES=$2
if [ ! "$TS" ]; then
	TS=dams
fi
if [ ! "$ES" ]; then
	ES=events
fi
$BASE/ts-clear.sh $TS
$BASE/ts-load.sh $TS src/sample/predicates/
$BASE/ts-load.sh $TS src/sample/object
$BASE/ts-clear.sh $ES
$BASE/ts-load.sh $ES src/sample/predicates/
$BASE/ts-load.sh $ES src/sample/events
