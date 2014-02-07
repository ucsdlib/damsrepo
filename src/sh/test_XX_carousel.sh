#!/bin/sh

# load env
BASE=`dirname $0`
source $BASE/common.sh

function load
{
	ARK=$1
	FILE=$2

    # load source image
    $BASE/fs-post.sh $ARK 1.jpg $BASE/../sample/files/carousel/$FILE

    # generate derivatives
    $BASE/fs-derivatives.sh $ARK 1.jpg
}
load bd3413814d Annese_1.jpg
load bd21510035 Levy_1.jpg
load bd08540633 OT_2.jpg
load bd95572101 SIO_3.jpg
load bd82602702 Wagner_2.jpg
load bd6963330m bd6963330m.jpg
load bd4369453c bd4369453c.jpg
load bd5666392f bd5666392f.jpg
load bd3106642r bd3106642r.jpg
load bd18097029 bd18097029.jpg
