#!/bin/sh

ERRORS=0

# list units
echo "Listing units"
UNIT_LIST=`curl -s -f http://localhost:8080/dams/api/units`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# parse unit list
UNIT=`echo $UNIT_LIST | perl -pe 's/></>\n</g' | grep "<unit>" | head -1 | perl -pe "s/<\/unit>//" | perl -pe "s/.*\///"`
echo "Unit: $UNIT"

# list objects in the unit
echo "Listing objects in unit $UNIT"
OBJ_LIST=`curl -s -f http://localhost:8080/dams/api/units/$UNIT`
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
curl -f -X POST http://localhost:8080/dams/api/objects/$OBJ/index
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
echo http://localhost:8080/dams/api/index?$IDS
curl -f -X POST "http://localhost:8080/dams/api/index?$IDS"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
    exit 1
fi
echo
echo

echo "Search solr"
curl -f -X GET http://localhost:8080/dams/api/index?q=test
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "Remove a record from solr"
curl -f -X DELETE http://localhost:8080/dams/api/objects/$OBJ/index
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo
echo

echo "Bulk delete records from solr index"
curl -f -X DELETE "http://localhost:8080/dams/api/index?$IDS"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo
echo


echo ERRORS: $ERRORS
exit $ERRORS
