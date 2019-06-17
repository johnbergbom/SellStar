#!/bin/bash
# ssh tunnel for accessing the production database at sellstar.fi

# Sleep for three minutes before starting in order to let the mac
# get time finding the network connection.
sleep 180

while true
do
  ssh -o TCPKeepAlive=yes -o ServerAliveInterval=15 -L 5433:localhost:5432 sellstar.fi
  sleep 5
done
