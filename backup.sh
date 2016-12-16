#!/bin/bash
TIME=`date +%s`
mysqldump -h $DB_HOST --password=$DB_PASS --user=$DB_USER $DB | gzip > coachbot-$TIME.sql.gz
