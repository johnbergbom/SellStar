#!/bin/bash
cd /usr/local/huutonet/prod
java -jar commandrow.jar fetchtransactions `cat bc.txt`
java -jar commandrow.jar transfertoqd `cat bc.txt`
java -jar commandrow.jar fixfaultytransactions true
java -jar commandrow.jar fixfaultypayments
java -jar commandrow.jar confirmpayment
