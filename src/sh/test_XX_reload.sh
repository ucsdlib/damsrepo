#!/bin/sh

# load env
BASE=`dirname $0`
source $BASE/common.sh

$BASE/fs-clear.sh localStore     # clear file storage
$BASE/test_01_init.sh            # clear metadata and solr, load predicates
$BASE/test_XX_samples.sh         # load sample objects
$BASE/test_XX_formatsComplex.sh  # load format sampler files
$BASE/test_XX_carousel.sh        # load carousel image files

IDS="$IDS bb02020202" # units
IDS="$IDS bb48484848"
IDS="$IDS bd24241158" # collections
IDS="$IDS bb03030303"
IDS="$IDS bd5905304g"
IDS="$IDS bb01010101" # complex objects
IDS="$IDS bb80808080"
IDS="$IDS bb52572546"
IDS="$IDS bb55555555" # format sampler
IDS="$IDS bd5905379f" # carousel
IDS="$IDS bd3413814d"
IDS="$IDS bd21510035"
IDS="$IDS bd08540633"
IDS="$IDS bd95572101"
IDS="$IDS bd82602702"
IDS="$IDS bd6963330m"
IDS="$IDS bd4369453c"
IDS="$IDS bd5666392f"
IDS="$IDS bd3106642r"
IDS="$IDS bd18097029"

# solr indexing
cd $BASE/../../damspas
bin/damsolrizer-single --hydra_home . $IDS
