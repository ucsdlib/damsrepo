#!/bin/sh

# load sample complex object with sample format components

BASE=`dirname $0`

# post master files
$BASE/fs-post-cmp.sh bd0922518w 1 1.wav $BASE/../sample/files/audio.wav
$BASE/fs-post-cmp.sh bd0922518w 2 1.tar.gz $BASE/../sample/files/data.tar.gz
$BASE/fs-post-cmp.sh bd0922518w 3 1.pdf $BASE/../sample/files/document.pdf
$BASE/fs-post-cmp.sh bd0922518w 4 1.tif $BASE/../sample/files/image.tif
$BASE/fs-post-cmp.sh bd0922518w 5 1.mov $BASE/../sample/files/video.mov

# generate (or upload) derivatives
$BASE/fs-post-cmp.sh bd0922518w 1 2.mp3 $BASE/../sample/files/audio.mp3
$BASE/fs-derivatives-cmp.sh bd0922518w 3 1.pdf
$BASE/fs-derivatives-cmp.sh bd0922518w 4 1.tif
$BASE/fs-post-cmp.sh bd0922518w 5 2.mp4 $BASE/../sample/files/video.mp4
$BASE/fs-post-cmp.sh bd0922518w 5 3.jpg $BASE/../sample/files/video-preview.jpg
$BASE/fs-post-cmp.sh bd0922518w 5 4.jpg $BASE/../sample/files/video-thumbnail.jpg
$BASE/fs-post-cmp.sh bd0922518w 5 5.jpg $BASE/../sample/files/video-icon.jpg

# post object metadata
$BASE/ts-put.sh bd0922518w $BASE/../sample/object/formatSampleComplex.rdf.xml
