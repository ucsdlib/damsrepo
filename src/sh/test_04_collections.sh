#!/bin/sh

ERRORS=0

# list collections
RESP=`curl -f $URL/api/collections`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo $RESP

COL=`echo $RESP | perl -pe 's/></>\n</g' | grep "<collection>" | head -1 | perl -pe "s/<\/collection>//" | perl -pe "s/.*\///"`
echo "Collection: $COL"

# list objects in a collection
curl -f $URL/api/collections/$COL
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# count objects in a collection
curl -f $URL/api/collections/$COL/count
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# list files in a collection
curl -f $URL/api/collections/$COL/files
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "ERRORS: $ERRORS"
exit $ERRORS
