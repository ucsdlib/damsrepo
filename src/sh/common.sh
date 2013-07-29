#!/bin/sh

# dams.properties
if [ ! "$DAMS_HOME" -a -f "$BASE/../dams.properties" ]; then
	DAMS_HOME="$BASE/.."
fi
PROPS="$DAMS_HOME/dams.properties"

# classpath
CP="$BASE/../classes"
JARS="$BASE/../lib"
for i in "$JARS"/*.jar; do
	CP="$CP:$i"
done

# repo username/password
if [ -f "$DAMS_HOME/dams.yml" ]; then
	REPOCFG="$DAMS_HOME/dams.yml"
elif [ -f "$DAMSPAS_HOME/config/fedora.yml" ]; then
	REPOCFG="$DAMSPAS_HOME/config/fedora.yml"
fi
if [ -f "$REPOCFG" ]; then
	export USER=`grep -A 3 "^development:" "$REPOCFG" | grep user | cut -d: -f2 | tr -d ' '`
	export PASS=`grep -A 3 "^development:" "$REPOCFG" | grep password | cut -d: -f2 | tr -d ' '`
	export URL=`grep -A 3 "^development:" "$REPOCFG" | grep url | cut -d: -f2- | tr -d ' ' | sed -e's/\/fedora$//'`
	export SOLR_URL=`echo $URL | sed -e's/dams$/solr/'`
fi
if [ ! "$URL" ]; then
	URL="http://localhost:8080/dams"
	SOLR_URL="http://localhost:8080/solr"
fi

# solr indexing
if [ "$DAMSPAS_HOME" -a -d $DAMSPAS_HOME ]; then
	export INDEXER=$DAMSPAS_HOME
elif [ -d $BASE/../../hydration ]; then
	export INDEXER=$BASE/../../hydration
elif [ -d $BASE/../../damspas ]; then
	export INDEXER=$BASE/../../damspas
fi
