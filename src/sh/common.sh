#!/bin/sh

PROPS=$DAMS_HOME/dams.properties
DIR=$BASE/../webapp/WEB-INF
export DIR2=$BASE/../../src
CP=$DIR/classes
for i in $DIR2/lib/*.jar $DIR2/lib2/*.jar; do
	CP=$CP:$i
done

# lookup repo username/password
export USER=`grep -A 3 "^development:" $DAMSPAS_HOME/config/fedora.yml | grep user | cut -d: -f2 | tr -d ' '`
export PASS=`grep -A 3 "^development:" $DAMSPAS_HOME/config/fedora.yml | grep password | cut -d: -f2 | tr -d ' '`
