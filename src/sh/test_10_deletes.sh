#!/bin/sh

ERRORS=0

# list repositories
echo "Listing repositories"
REPO_LIST=`curl -s -f http://localhost:8080/dams/api/repositories`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse repository list
REPO=`echo $REPO_LIST | perl -pe 's/></>\n</g' | grep "<repository>" | head -1 | perl -pe "s/<\/repository>//" | perl -pe "s/.*\///"`
echo "Repo: $REPO"

# list objects in the repository
echo "Listing objects in repository $REPO"
OBJ_LIST=`curl -s -f http://localhost:8080/dams/api/repositories/$REPO`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse object list
OBJ=`echo $OBJ_LIST | perl -pe 's/></>\n</g' | grep "<obj>" | head -1 | perl -pe "s/<\/obj>//" | perl -pe "s/.*\///"`
echo "Object: $OBJ"

# get basic object metadata
echo "Basic object metadata"
OBJ_RDF=`curl -s -f http://localhost:8080/dams/api/objects/$OBJ`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# parse file list
FILE=`echo $OBJ_RDF | perl -pe 's/>/>\n/g' | grep "\.jpg\">" | head -1 | perl -pe "s/\">.*//" | perl -pe "s/.*$OBJ\///"`
echo "File: $FILE"

# delete a file
echo "Delete a file"
curl -f -X DELETE http://localhost:8080/dams/api/files/$OBJ/$FILE
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "Selective delete"
curl -f -X DELETE "http://localhost:8080/dams/api/objects/$OBJ?predicate=dams%3Adate"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "Delete an object
curl -f -X DELETE http://localhost:8080/dams/api/objects/$OBJ"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo ERRORS: $ERRORS
exit $ERRORS
