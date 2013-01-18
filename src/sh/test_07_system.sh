#!/bin/sh

ERRORS=0

# system info
echo "System info"
curl -f http://localhost:8080/dams/api/system/info
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system version
echo "System version"
curl -f http://localhost:8080/dams/api/system/version
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system filestores
echo "System filestores"
curl -f http://localhost:8080/dams/api/system/filestores
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system triplestores
echo "System triplestores"
curl -f http://localhost:8080/dams/api/system/triplestores
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system predicates
echo "System predicates"
curl -f http://localhost:8080/dams/api/system/predicates
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# user info
echo "User info"
curl -f http://localhost:8080/dams/api/client/info?user=escowles
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

echo ERRORS: $ERRORS
exit $ERRORS
