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

# initialize solr
$BASE/solr-clear.sh

# initialize object triplestore and load sample objects
$BASE/ts-clear.sh $TS
$BASE/ts-load.sh $TS src/sample/predicates/
$BASE/ts-load.sh $TS src/sample/object

# initialize event triplestore and load sample events
$BASE/ts-clear.sh $ES
$BASE/ts-load.sh $ES src/sample/predicates/
$BASE/ts-load.sh $ES src/sample/events
