#!/bin/sh

# load hierarchical complex object sample record

BASE=`dirname $0`
ARK=bb81818181

TS=$1
ES=$2
if [ ! "$TS" ]; then
	TS=dams
fi
if [ ! "$ES" ]; then
	ES=events
fi

# delete object
$BASE/ts-delete.sh $ARK

# delete files
$BASE/fs-delete.sh $ARK 1/1.pdf
$BASE/fs-delete.sh $ARK 3/1.jpg
$BASE/fs-delete.sh $ARK 4/1.jpg
for i in 1 3 4; do
	for j in 2 3 4 5; do
		echo $i/$j
		$BASE/fs-delete.sh $ARK $i/$j.jpg
	done
done

# check whether object exists
EXIST=`curl http://localhost:8080/dams/api/objects/$ARK/exists | grep -c "<statusCode>200</statusCode>"`
if [ $EXIST ]; then
	# object exists in some form, use PUT to update with fresh metadata
	echo "Updating object..."
	$BASE/ts-put.sh $ARK src/sample/object/damsComplexObject2.rdf.xml
else
	# first time loading object, use POST
	echo "Creating object..."
	$BASE/ts-post.sh $ARK src/sample/object/damsComplexObject2.rdf.xml
fi

# post files
$BASE/fs-post-cmp.sh $ARK 1 1.pdf src/sample/files/20775-bb01034796-1-1.pdf
$BASE/fs-post-cmp.sh $ARK 3 1.jpg src/sample/files/20775-bb75097630-1-1.jpg
$BASE/fs-post-cmp.sh $ARK 4 1.jpg src/sample/files/20775-bb01010101-2-1.jpg

# generate derivatives
$BASE/fs-derivatives-cmp.sh $ARK 1 1.pdf
$BASE/fs-derivatives-cmp.sh $ARK 3 1.jpg
$BASE/fs-derivatives-cmp.sh $ARK 4 1.jpg
