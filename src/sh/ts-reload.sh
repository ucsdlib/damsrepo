#!/bin/sh

# clear triplestore and load sample data

BASE=`dirname $0`

TS=$1
$BASE/ts-clear.sh $TS
$BASE/ts-load.sh $TS src/sample/predicates/
$BASE/ts-load.sh $TS src/sample/object
