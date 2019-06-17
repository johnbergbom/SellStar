#!/bin/bash
# Script for getting access to the database remotely

# Sleep for three minutes before starting in order to let the mac
# get time finding the network connection.
sleep 180

while true
do
  ssh -o TCPKeepAlive=yes -o ServerAliveInterval=15 -R :5432:localhost:5432 sellstar.fi sleep 10800
  sleep 5
done
