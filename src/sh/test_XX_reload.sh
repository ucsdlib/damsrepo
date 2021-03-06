#!/bin/sh

# load env
BASE=`dirname $0`
. $BASE/common.sh

$BASE/fs-clear.sh localStore     # clear file storage
$BASE/test_01_init.sh            # clear metadata and solr, load predicates
$BASE/test_XX_samples.sh         # load sample objects
$BASE/test_XX_formatsComplex.sh  # load format sampler files (complex object)
$BASE/test_XX_formats.sh         # load format sampler files (simple objects)
$BASE/test_XX_reindex.sh         # (re)index sample records in solr
