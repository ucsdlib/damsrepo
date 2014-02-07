#!/bin/sh

ERRORS=0

# system info
echo "System info"
curl -f $URL/api/system/info
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system version
echo "System version"
curl -f $URL/api/system/version
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system filestores
echo "System filestores"
curl -f $URL/api/system/filestores
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system triplestores
echo "System triplestores"
curl -f $URL/api/system/triplestores
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# system predicates
echo "System predicates"
curl -f $URL/api/system/predicates
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

# user info
echo "User info"
curl -f $URL/api/client/info?user=escowles
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi

echo ERRORS: $ERRORS
exit $ERRORS
