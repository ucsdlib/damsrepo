#!/bin/sh

ERRORS=0

# list units
RESP=`curl -f $URL/api/units`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo $RESP

UNIT=`echo $RESP | perl -pe 's/></>\n</g' | grep "<unit>" | head -1 | perl -pe "s/<\/unit>//" | perl -pe "s/.*\///"`
echo "Unit: $UNIT"

# list objects in a unit
curl -f $URL/api/units/$UNIT
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# count objects in a unit
curl -f $URL/api/units/$UNIT/count
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# list files in a unit
curl -f $URL/api/units/$UNIT/files
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "ERRORS: $ERRORS"
exit $ERRORS
