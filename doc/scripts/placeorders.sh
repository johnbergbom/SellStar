#!/bin/bash
#
# updateProviderPrices and placeOrders cannot run at the same time
# since both use the order placing function of FocalPrice. However
# updateProviderPrices doesn't need to be ran as often as placeOrders,
# so only start it one third of the time.

cd /usr/local/huutonet/prod

i=0
while true
do
  # sleep for three hours
  sleep 10800

  # Don't start updateProviderPrices so often
  i=`expr $i + 1`
  if [ $i -eq 3 ]; then
    java -jar commandrow.jar updateProviderPrices
  fi
  if [ $i -ge 3 ]; then
    i=0
  fi

  java -jar commandrow.jar placeorders `cat pc.txt`
done
