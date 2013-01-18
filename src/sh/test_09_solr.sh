#!/bin/sh

ERRORS=0

# list repositories
echo "Listing repositories"
REPO_LIST=`curl -s -f http://localhost:8080/dams/api/repositories`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# parse repository list
REPO=`echo $REPO_LIST | perl -pe 's/></>\n</g' | grep "<repository>" | head -1 | perl -pe "s/<\/repository>//" | perl -pe "s/.*\///"`
echo "Repo: $REPO"

# list objects in the repository
echo "Listing objects in repository $REPO"
OBJ_LIST=`curl -s -f http://localhost:8080/dams/api/repositories/$REPO`
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
echo $IDS
curl -f -X POST http://localhost:8080/dams/api/index?$IDS
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
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
curl -f -X DELETE http://localhost:8080/dams/api/index?$IDS
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo
echo


echo ERRORS: $ERRORS
exit $ERRORS
