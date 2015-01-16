#!/bin/sh

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
ES=$2
if [ ! "$TS" ]; then
	TS=dams
fi
if [ ! "$ES" ]; then
	ES=events
fi

ERRORS=0

# initialize object triplestore
$BASE/ts-clear.sh $TS
ERRORS=$(( $ERRORS + $? ))

# load predicate map into object triplestore
$BASE/ts-load-raw.sh $TS $BASE/../sample/predicates/
ERRORS=$(( $ERRORS + $? ))

# initialize event triplestore
$BASE/ts-clear.sh $ES
ERRORS=$(( $ERRORS + $? ))

# load predicate map into event triplestore
$BASE/ts-load-raw.sh $ES $BASE/../sample/predicates/
ERRORS=$(( $ERRORS + $? ))

# clear solr index
curl $SOLR_URL/blacklight/update?commit=true -H "Content-Type: text/xml" --data-binary "<delete><query>*:*</query></delete>"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + $1 ))
fi

echo ERRORS: $ERRORS
exit $ERRORS
