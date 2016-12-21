#!/bin/bash

FILE=$1;

if [ -f "${FILE}" ]
then
  echo "Restoring from '${FILE}'"
  echo "drop database coachbot" | mysql -u root
  echo "create database coachbot default character set utf8" | mysql -u root
  cat ${FILE} | gunzip | mysql -u root coachbot
  echo "Done."
else
  echo "Please specify a file to restore"
fi