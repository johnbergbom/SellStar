#!/bin/bash
#
# Very simple script for backing up:
# 1.) database
# 2.) log files
# 3.) application binary
# 4.) scripts
# 5.) product images
#
# Copyright 2010 QD-Tech Oy
# Author: John Bergbom

# Initiate some variables
BASEDIR=/usr/local/huutonet/prod
DESTDIR=/usr/local/backup
cd $BASEDIR/scripts
. pg_huutonet.env
DATE=`date +%F`
DB_BACKUP_COMMAND=/Library/PostgreSQL/8.4/bin/pg_dump

echo Backing up the database straight at sellstar.fi
#$DB_BACKUP_COMMAND -F c -U huutonet huutonet > $DESTDIR/db/huutonet.pg_dump.$DATE
ssh sellstar.fi "pg_dump -F c -U huutonet huutonet > /backup/db/huutonet.latest_pg_dump << EOF
$PGPASSWORD
EOF"
echo Copying the database backup to a different disc at sellstar.fi
#scp $DESTDIR/db/huutonet.pg_dump.$DATE sellstar.fi:backup/db/huutonet.latest_pg_dump
ssh sellstar.fi "cp /backup/db/huutonet.latest_pg_dump /backup/db/huutonet.pg_dump.$DATE"
echo Copying the database backup to local computer
scp sellstar.fi:/backup/db/huutonet.latest_pg_dump $DESTDIR/db/huutonet.pg_dump.$DATE

echo Backing up the svn repository straight at sellstar.fi
ssh sellstar.fi "/usr/local/bin/svnbackup"

echo Backing up the log files
cd $BASEDIR/log
#for var in `ls`
#do
#  if [ ! -e $DESTDIR/log/$var.gz ]
#  then
#    gzip -c $var > $DESTDIR/log/$var.gz
#  fi
#done
tar -cf logs.tar debug* info* error*
#gzip -f logs.tar
#mv logs.tar.gz $DESTDIR/log
echo Copying the logfiles to sellstar.fi
rsync --progress logs.tar sellstar.fi:/backup/logs
rm logs.tar

echo Backing up the binary
#cp $BASEDIR/commandrow.jar $DESTDIR
rsync --progress $BASEDIR/commandrow.jar sellstar.fi:/backup

echo Backing up the scripts
cd $BASEDIR/scripts
tar -cf $DESTDIR/scripts/scripts.$DATE.tar *
gzip -f $DESTDIR/scripts/scripts.$DATE.tar
scp $DESTDIR/scripts/scripts.$DATE.tar.gz sellstar.fi:/backup/scripts
rm $DESTDIR/scripts/scripts.$DATE.tar.gz

echo Backing up product images
cd $BASEDIR
tar -cf $DESTDIR/productimages.tar productimages
#gzip -f $DESTDIR/productimages.tar
echo Copying product images to sellstar.fi
rsync --progress $DESTDIR/productimages.tar sellstar.fi:/backup/images
rm $DESTDIR/productimages.tar
