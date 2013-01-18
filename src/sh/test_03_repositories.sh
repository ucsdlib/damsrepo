#!/bin/sh

ERRORS=0

# list repositories
RESP=`curl -f http://localhost:8080/dams/api/repositories`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo $RESP

REPO=`echo $RESP | perl -pe 's/></>\n</g' | grep "<repository>" | head -1 | perl -pe "s/<\/repository>//" | perl -pe "s/.*\///"`
echo "Repo: $REPO"

# list objects in a repository
curl -f http://localhost:8080/dams/api/repositories/$REPO
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# count objects in a repository
curl -f http://localhost:8080/dams/api/repositories/$REPO/count
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# list files in a repository
curl -f http://localhost:8080/dams/api/repositories/$REPO/files
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo "ERRORS: $ERRORS"
exit $ERRORS
