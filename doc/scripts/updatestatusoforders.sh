#!/bin/bash
cd /usr/local/huutonet/prod

while true
do
  # sleep for six hours
  sleep 21600
  java -jar commandrow.jar updateStatusOfOrders
  java -jar commandrow.jar updateProviderCommunication
  java -jar commandrow.jar housekeeping
done

