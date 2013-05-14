#!/bin/sh

# load sample complex object with sample format components

BASE=`dirname $0`

# delete master files and derivatives
$BASE/fs-delete.sh bb55555555 1/1.wav # wav master
$BASE/fs-delete.sh bb55555555 1/2.mp3 # mp3 service
$BASE/fs-delete.sh bb55555555 2/1.tar.gz # data tarball
for fid in 1.pdf 2.jpg 3.jpg 4.jpg 5.jpg; do # document: pdf master, jpeg derivs
	$BASE/fs-delete.sh bb55555555 3/$fid
done
for fid in 1.tif 2.jpg 3.jpg 4.jpg 5.jpg; do # image: tif master, jpeg derivs
	$BASE/fs-delete.sh bb55555555 4/$fid
done
for fid in 1.mov 2.mp4 3.jpg 4.jpg 5.jpg; do # video: mov master, mp4/jpeg derivs
	$BASE/fs-delete.sh bb55555555 5/$fid
done

# delete object metadata
$BASE/ts-delete.sh bb55555555

# post master files
$BASE/fs-post-cmp.sh bb55555555 1 1.wav $BASE/../sample/files/audio.wav
$BASE/fs-post-cmp.sh bb55555555 2 1.tar.gz $BASE/../sample/files/data.tar.gz
$BASE/fs-post-cmp.sh bb55555555 3 1.pdf $BASE/../sample/files/20775-bb01034796-1-1.pdf
$BASE/fs-post-cmp.sh bb55555555 4 1.tif $BASE/../sample/files/20775-bb01010101-1-1.tif
$BASE/fs-post-cmp.sh bb55555555 5 1.mov $BASE/../sample/files/video.mov

# generate (or upload) derivatives
$BASE/fs-post-cmp.sh bb55555555 1 2.mp3 $BASE/../sample/files/audio.mp3
$BASE/fs-derivatives-cmp.sh bb55555555 3 1.pdf
$BASE/fs-derivatives-cmp.sh bb55555555 4 1.tif
$BASE/fs-post-cmp.sh bb55555555 5 2.mp4 $BASE/../sample/files/video.mp4
$BASE/fs-post-cmp.sh bb55555555 5 3.jpg $BASE/../sample/files/video-preview.jpg
$BASE/fs-post-cmp.sh bb55555555 5 4.jpg $BASE/../sample/files/video-thumbnail.jpg
$BASE/fs-post-cmp.sh bb55555555 5 5.jpg $BASE/../sample/files/video-icon.jpg

# post object metadata
$BASE/ts-put.sh bb55555555 $BASE/../sample/object/formatSampleComplex.rdf.xml
