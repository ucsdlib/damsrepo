#!/bin/sh

# load sample data

BASE=`dirname $0`

TS=$1
ES=$2
if [ ! "$TS" ]; then
	TS=dams
fi
if [ ! "$ES" ]; then
	ES=events
fi

# load sample objects
$BASE/ts-load.sh $TS $BASE/../sample/object

# load sample events
$BASE/ts-load.sh $ES $BASE/../sample/events

# post pdf
$BASE/fs-post-cmp.sh bb80808080 1 1.pdf $BASE/../sample/files/20775-bb01034796-1-1.pdf

# generate derivatives
$BASE/fs-derivatives-cmp.sh bb80808080 1 1.pdf

# post jpg
$BASE/fs-post-cmp.sh bb80808080 2 1.jpg $BASE/../sample/files/20775-bb75097630-1-1.jpg

# generate derivatives
$BASE/fs-derivatives-cmp.sh bb80808080 2 1.jpg

# post tif
$BASE/fs-post.sh bb90909090 1.tif $BASE/../sample/files/20775-bb01010101-1-1.tif

# generate derivatives
$BASE/fs-derivatives.sh bb90909090 1.tif
