#!/bin/sh

# load hierarchical complex object sample record

BASE=`dirname $0`
ARK=bb90909090

# delete object
$BASE/ts-delete.sh $ARK

# delete files
$BASE/fs-delete.sh $ARK 1.pdf
for j in 2 3 4 5; do
	echo $i/$j
	$BASE/fs-delete.sh $ARK $j.jpg
done

# post first files
$BASE/fs-post.sh $ARK 1.pdf $BASE/../sample/files/document.pdf

# update metadata (PUT required even for new obj b/c files create object)
$BASE/ts-put.sh $ARK $BASE/../sample/object/damsSimpleObject1.rdf.xml

# generate derivatives
$BASE/fs-derivatives.sh $ARK 1.pdf
