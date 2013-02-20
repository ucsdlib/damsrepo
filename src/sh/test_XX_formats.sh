#!/bin/sh

# load sample objects for different formats

BASE=`dirname $0`

# audio (wav master, mp3 service)
$BASE/fs-delete.sh bb51515151 1.wav
$BASE/fs-delete.sh bb51515151 2.mp3
$BASE/ts-delete.sh bb50505050
$BASE/fs-post.sh bb50505050 1.wav src/sample/files/audio.wav
$BASE/fs-post.sh bb50505050 2.mp3 src/sample/files/audio.mp3
$BASE/ts-put.sh bb50505050 src/sample/object/formatSampleAudio.rdf.xml

# data (tarball)
$BASE/fs-delete.sh bb51515151 1.tar.gz
$BASE/ts-delete.sh bb51515151
$BASE/fs-post.sh bb51515151 1.tar.gz src/sample/files/data.tar.gz
$BASE/ts-put.sh bb51515151 src/sample/object/formatSampleData.rdf.xml

# document (pdf master, jpeg derivatives)
for fid in 1.pdf 2.jpg 3.jpg 4.jpg 5.jpg; do
	$BASE/fs-delete.sh bb52525252 $fid
done
$BASE/ts-delete.sh bb52525252
$BASE/fs-post.sh bb52525252 1.pdf src/sample/files/20775-bb01034796-1-1.pdf
$BASE/ts-put.sh bb52525252 src/sample/object/formatSampleDocument.rdf.xml
$BASE/fs-derivatives.sh bb52525252 1.pdf

# image (tif master, jpeg derivatives)
for fid in 1.tif 2.jpg 3.jpg 4.jpg 5.jpg; do
	$BASE/fs-delete.sh bb53535353 $fid
done
$BASE/ts-delete.sh bb53535353
$BASE/fs-post.sh bb53535353 1.tif src/sample/files/20775-bb01010101-1-1.tif
$BASE/ts-put.sh bb53535353 src/sample/object/formatSampleImage.rdf.xml
$BASE/fs-derivatives.sh bb53535353 1.tif

# video (mov master, mp4 and jpeg derivatives)
for fid in 1.mov 2.mp4 3.jpg 4.jpg 5.jpg; do
	$BASE/fs-delete.sh bb54545454 $fid
done
$BASE/ts-delete.sh bb54545454
$BASE/fs-post.sh bb54545454 1.mov src/sample/files/video.mov
$BASE/fs-post.sh bb54545454 2.mp4 src/sample/files/video.mp4
$BASE/fs-post.sh bb54545454 3.jpg src/sample/files/video-preview.jpg
$BASE/fs-post.sh bb54545454 4.jpg src/sample/files/video-thumbnail.jpg
$BASE/fs-post.sh bb54545454 5.jpg src/sample/files/video-icon.jpg
$BASE/ts-put.sh bb54545454 src/sample/object/formatSampleVideo.rdf.xml
