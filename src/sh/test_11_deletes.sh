#!/bin/sh

# test deleting records
BASE=`dirname $0`
. $BASE/common.sh

ERRORS=0

# list units
echo "Listing units"
UNIT_LIST=`curl -u $USER:$PASS -s -f $URL/api/units`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse unit list
UNIT=`echo $UNIT_LIST | perl -pe 's/></>\n</g' | grep "<unit>" | head -1 | perl -pe "s/<\/unit>//" | perl -pe "s/.*\///"`
echo "Unit: $UNIT"

# list objects in the unit
echo "Listing objects in unit $UNIT"
OBJ_LIST=`curl -u $USER:$PASS -s -f $URL/api/units/$UNIT`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse object list
OBJ=`echo $OBJ_LIST | perl -pe 's/></>\n</g' | grep "<obj>" | head -1 | perl -pe "s/<\/obj>//" | perl -pe "s/.*\///"`
echo "Object: $OBJ"

# get basic object metadata
echo "Basic object metadata"
OBJ_RDF=`curl -u $USER:$PASS -s -f $URL/api/objects/$OBJ`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse file list
FILE=`echo $OBJ_RDF | perl -pe 's/>/>\n/g' | grep "\.jpg\">" | head -1 | perl -pe "s/\">.*//" | perl -pe "s/.*$OBJ\///"`
echo "File: $FILE"

# delete a file
echo "Delete a file"
curl -u $USER:$PASS -f -X DELETE $URL/api/files/$OBJ/$FILE
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "Delete an object
curl -u $USER:$PASS -f -X DELETE $URL/api/objects/$OBJ"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo ERRORS: $ERRORS
exit $ERRORS
