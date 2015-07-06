#!/bin/sh

# test object operations
BASE=`dirname $0`
. $BASE/common.sh

ERRORS=0

# list units
echo "Listing units"
UNIT_LIST=`curl -u $USER:$PASS -s -f $URL/api/units`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse units list
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
OBJ_RDF=`curl -u $USER:$PASS -f $URL/api/objects/$OBJ`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo $OBJ_RDF

# get recursive object metadata
echo "Recursive object metadata"
curl -u $USER:$PASS -f $URL/api/objects/$OBJ/export
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# test object existence
echo "Test object existence"
curl -u $USER:$PASS -f $URL/api/objects/$OBJ/exists
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# metadata transform (retrieval only)
echo "Metadata transform (retrieval only)"
curl -u $USER:$PASS -f "$URL/api/objects/$OBJ/transform?recursive=true&xsl=solrindexer.xsl"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# metadata transform (save output as new file)
echo "Metadata transform (save output as new file)"
curl -u $USER:$PASS -f -X POST "$URL/api/objects/$OBJ/transform?recursive=true&xsl=solrindexer.xsl&dest=6.xml"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# metadata transform (save output as new file) - make sure file was created
echo "Metadata transform (save output as new file) - make sure file was created"
curl -u $USER:$PASS -f "$URL/api/files/$OBJ/6.xml"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# find an event record
echo "Find an event record"
EVENT=`echo $OBJ_RDF | perl -pe 's/>/>\n/g' | grep "<dams:event" | head -1 | sed -e"s/\"\/>//" | sed -e"s/.*\///"`
echo "Event: $EVENT"

# retrieve an event record
echo "Retrieve an event record"
curl -u $USER:$PASS -f $URL/api/events/$EVENT
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo ERRORS: $ERRORS
exit $ERRORS
