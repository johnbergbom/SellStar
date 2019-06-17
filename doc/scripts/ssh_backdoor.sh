#!/bin/bash
# Script for getting access to the mac from sellstar.fi

# Sleep for three minutes before starting in order to let the mac
# get time finding the network connection.
sleep 180

while true
do
  ssh -o TCPKeepAlive=yes -o ServerAliveInterval=15 -R 5000:localhost:22 sellstar.fi sleep 10800
  sleep 5
done
