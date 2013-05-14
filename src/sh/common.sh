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
if [ "$DAMSPAS_HOME" -a -f "$DAMSPAS_HOME/config/fedora.yml" ]; then
	REPOCFG="$DAMSPAS_HOME/config/fedora.yml"
elif [ -f "$DAMS_HOME/dams.yml" ]; then
	REPOCFG="$DAMS_HOME/dams.yml"
fi
if [ -f "$REPOCFG" ]; then
	export USER=`grep -A 3 "^development:" "$REPOCFG" | grep user | cut -d: -f2 | tr -d ' '`
	export PASS=`grep -A 3 "^development:" "$REPOCFG" | grep password | cut -d: -f2 | tr -d ' '`
	export URL=`grep -A 3 "^development:" "$REPOCFG" | grep url | cut -d: -f2 | tr -d ' '`
fi
