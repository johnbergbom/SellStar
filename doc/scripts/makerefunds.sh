#!/bin/bash
cd /usr/local/huutonet/prod
java -jar commandrow.jar makerefunds `cat bc.txt`
