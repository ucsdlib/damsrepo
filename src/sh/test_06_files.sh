#!/bin/sh

ERRORS=0

# list units
echo "Listing units"
UNIT_LIST=`curl -s -f $URL/api/units`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse units list
UNIT=`echo $UNIT_LIST | perl -pe 's/></>\n</g' | grep "<unit>" | head -1 | perl -pe "s/<\/unit>//" | perl -pe "s/.*\///"`
echo "Unit: $UNIT"

# list objects in the unit
echo "Listing objects in unit $UNIT"
OBJ_LIST=`curl -s -f $URL/api/units/$UNIT`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse object list
OBJ=`echo $OBJ_LIST | perl -pe 's/></>\n</g' | grep "<obj>" | head -1 | perl -pe "s/<\/obj>//" | perl -pe "s/.*\///"`
echo "Object: $OBJ"

# get basic object metadata
echo "Basic object metadata"
OBJ_RDF=`curl -s -f $URL/api/objects/$OBJ`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse file list
FILE=`echo $OBJ_RDF | perl -pe 's/>/>\n/g' | grep "\.jpg\">" | head -1 | perl -pe "s/\">.*//" | perl -pe "s/.*$OBJ\///"`
echo "File: $FILE"

# retrieve a file
echo "Retrieve a file"
curl -s -f -o /dev/null $URL/api/files/$OBJ/$FILE
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# test whether a file exists
echo "Test whether a file exists"
curl -f $URL/api/files/$OBJ/$FILE/exists
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# fixity check
echo "Fixity check"
curl -f $URL/api/files/$OBJ/$FILE/fixity
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# characterization
echo "Characterize"
curl -f $URL/api/files/$OBJ/$FILE/characterize
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo ERRORS: $ERRORS
exit $ERRORS
