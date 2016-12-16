#!/bin/bash

mysqldump -h $DB_HOST --password=$DB_PASS --user=$DB_USER $DB | gzip > coachbot.sql.gz
