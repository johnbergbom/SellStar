#!/bin/bash
cd /usr/local/huutonet/prod

java -jar commandrow.jar handlereceivedfeedback
java -jar commandrow.jar givefeedback
