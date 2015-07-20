#!/bin/sh

# load env
BASE=`dirname $0`
. $BASE/common.sh

$BASE/fs-clear.sh localStore     # clear file storage
$BASE/test_01_init.sh            # clear metadata and solr, load predicates
