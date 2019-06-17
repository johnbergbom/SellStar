#!/bin/bash
cd /usr/local/huutonet/prod

# Sleep for five minutes before starting in order to let the mac
# get time finding the network connection.
sleep 300

while true
do
  java -jar commandrow.jar checkemail
  # sleep for five minutes
  sleep 300
done
