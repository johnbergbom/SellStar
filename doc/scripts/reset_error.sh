#!/bin/bash

BASEDIR=/usr/local/huutonet/prod
. $BASEDIR/scripts/pg_huutonet.env

/Library/PostgreSQL/8.4/bin/psql huutonet huutonet << EOF
update settings set value = 'false' where key = 'error';
EOF
