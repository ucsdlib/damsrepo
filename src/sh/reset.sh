#!/bin/sh

BASE=`dirname $0`

# clear triplestore and solr
$BASE/test_01_init.sh

# clear filestore
rm -rf $DAMS_HOME/localStore/*

# load sample data
$BASE/test_XX_samples.sh

# index units and collections
$DAMSPAS_HOME/bin/damsolrizer-single --hydra_home $DAMSPAS_HOME bb02020202 bb48484848 bd5905304g bd24241158 bb80808080
