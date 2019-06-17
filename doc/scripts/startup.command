#!/bin/bash

/usr/local/huutonet/prod/scripts/prod_db.sh &
/usr/local/huutonet/prod/scripts/placeorders.sh &
/usr/local/huutonet/prod/scripts/updatestatusoforders.sh &
/usr/local/huutonet/prod/scripts/checkemail.sh &
#/usr/local/huutonet/prod/scripts/remotedb.sh &
/usr/local/huutonet/prod/scripts/ssh_backdoor.sh &
#/usr/local/huutonet/prod/scripts/hourly_run.sh listitems Huutonet &
#/usr/local/huutonet/prod/scripts/hourly_run.sh listitems Mikko &
/usr/local/huutonet/prod/scripts/listitems.sh &
