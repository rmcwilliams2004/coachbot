#!/bin/bash
VF=.vars.sh
heroku config -s > $VF
source $VF
export DB_HOST=`echo $DB_URL | sed -e 's/jdbc:mysql:\/\///g' | sed -e 's/:.*//g'`
export DB=`echo $DB_URL | sed -e 's/jdbc:mysql:.*:3306\///g' | sed -e 's/?.*//g'`
export DB_USER
export DB_PASS
rm -f $VF
./backup.sh
