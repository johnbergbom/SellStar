#!/bin/bash
cd /usr/local/huutonet/prod

while true
do
  # sleep for two hours
  sleep 7200
  java -jar commandrow.jar listitems
  java -jar commandrow.jar payForHighlightedAds
done
