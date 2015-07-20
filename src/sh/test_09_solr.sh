#!/bin/sh

# test solr
BASE=`dirname $0`
. $BASE/common.sh

ERRORS=0

# list units
echo "Listing units"
UNIT_LIST=`curl -u $USER:$PASS -s -f $URL/api/units`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# parse unit list
UNIT=`echo $UNIT_LIST | perl -pe 's/></>\n</g' | grep "<unit>" | head -1 | perl -pe "s/<\/unit>//" | perl -pe "s/.*\///"`
echo "Unit: $UNIT"

# list objects in the unit
echo "Listing objects in unit $UNIT"
OBJ_LIST=`curl -u $USER:$PASS -s -f $URL/api/units/$UNIT`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# parse object list
OBJ=`echo $OBJ_LIST | perl -pe 's/></>\n</g' | grep "<obj>" | head -1 | perl -pe "s/<\/obj>//" | perl -pe "s/.*\///"`
OBJS=`echo $OBJ_LIST | perl -pe 's/></>\n</g' | grep "<obj>" | perl -pe "s/<\/obj>//" | perl -pe "s/.*\///"`
for i in $OBJS; do
	echo Object: $i
done
echo

echo "Index a record in solr"
curl -u $USER:$PASS -f -X POST $URL/api/objects/$OBJ/index
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo
echo

echo "Bulk index multiple records in solr"
IDS=
for i in $OBJS; do
	IDS="${IDS}id=$i&"
done
echo $URL/api/index?$IDS
curl -u $USER:$PASS -f -X POST "$URL/api/index?$IDS"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
    exit 1
fi
echo
echo

echo "Search solr"
curl -u $USER:$PASS -f -X GET $URL/api/index?q=test
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "Remove a record from solr"
curl -u $USER:$PASS -f -X DELETE $URL/api/objects/$OBJ/index
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo
echo

echo "Bulk delete records from solr index"
curl -u $USER:$PASS -f -X DELETE "$URL/api/index?$IDS"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo
echo


echo ERRORS: $ERRORS
exit $ERRORS
