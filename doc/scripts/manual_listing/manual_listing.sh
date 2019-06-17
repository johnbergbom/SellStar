#!/bin/bash
#
# Script for listing several products without using SellStar.
#
# Copyright 2010 QD-Tech Oy
# Author: John Bergbom
#
# The same category is used for all the products in a batch, so in order to use this the following is recommended:
# 1.) Choose a huutonet category for the products you want to add an figure out the number of the category
# 2.) Choose several products of the same category and add them all at the same time
#
# for categ in 343 374 4 531 571 808 156 550 826 341 348 364 811 365 387 7 164 543 558 363 810 355 361 372 6 913 3
# do
#   ./manual_listing.sh  -u UserName -p somepass -s AUCTION -e 1 -r NEW -l Saksa -d "Tuote lähetetään saksasta. Ostaja maksaa postikulut." -v 0 -c ${categ} -f fk-products_category_${categ}.txt
# done
#


. ./functions

# Uncomment to get more debug information for the script
#set -x

# Initiate some variables
#BASEDIR=/tmp/manual_listing
BASEDIR=./manual_listing
CURRDATE=`date +%F`
LOGFILE="$BASEDIR/log/manual_listing.log.$CURRDATE"
mkdir -p "$BASEDIR/log"

# Setting this one to false gives text style ads. NOTE: without html output there will be one image at the most.
USE_HTML_OUTPUT=true

function print_parameters() {
  echo Usage: manual_listing.sh [params]
  echo "List of parameters"
  echo "  -u market username"
  echo "  -p market password"
  echo "  -s selling type (one of AUCTION BUY_NOW AUCTION_AND_BUY_NOW)"
  echo "  -e expiring in days"
  echo "  -r product condition (one of NEW AS_NEW GOOD OK POOR)"
  echo "  -l location"
  echo "  -d delivery description"
  echo "  -q quantity (default 1)"
  echo "  -v vat percentage (0 or 23)"
  echo "  -c category (integer)"
  echo "  -f file to read product url's from"
  echo "  -o old log file to list old products again"
}

PRODUCT_TITLE=
PRODUCT_DESCRIPTION=
PRODUCT_SPECIFICATION=
SELLING_TYPE=
EXPIRATION_TIME=
PRODUCT_CONDITION=
PRODUCT_LOCATION=
VAT_PERC=
QUANTITY=
SHIPPING_COST=
DELIVERY_METHOD=
DELIVERY_DESCRIPTION=
PAYMENT_METHOD=

#echo "OPTIND starts at $OPTIND"
while getopts ":u:p:s:e:r:l:d:q:v:c:f:o:" optname
  do
    case "$optname" in
      "u")
        market_username=$OPTARG
        ;;
      "p")
	market_password=$OPTARG
        ;;
      "s")
	SELLING_TYPE=$OPTARG
        ;;
      "e")
	expiring_in_days=$OPTARG
        ;;
      "r")
	PRODUCT_CONDITION=$OPTARG
        ;;
      "l")
	PRODUCT_LOCATION=$OPTARG
        ;;
      "d")
	DELIVERY_DESCRIPTION=$OPTARG
        ;;
      "q")
	QUANTITY=$OPTARG
        ;;
      "v")
	VAT_PERC=$OPTARG
        ;;
      "c")
	CATEGORY=$OPTARG
        ;;
      "f")
	PRODUCT_FILE_LIST=$OPTARG
        ;;
      "o")
	OLD_LOGFILE=$OPTARG
        ;;
      "?")
        echo "Unknown option $OPTARG"
        print_parameters
        exit
        ;;
      ":")
        echo "No argument value for option $OPTARG"
        print_parameters
        exit
        ;;
      *)
      # Should not occur
        echo "Unknown error while processing options"
        print_parameters
        exit
        ;;
    esac
    #echo "OPTIND is now $OPTIND"
  done

if [[ ! -z $OLD_LOGFILE ]]; then
  if [[ `echo $OLD_LOGFILE | sed 's/.*\///'` == `echo $LOGFILE | sed 's/.*\///'` ]]; then
    echo "Refusing to use current logfile."
    exit
  fi
  egrep "Starting new run|Chosen selling type: |Expiring in days: |Chosen product condition: " $OLD_LOGFILE
#TODO: do this one loppuun
fi

exit

# Start by printing some information
#echo `LANG="en_EN@euro" date "+%F %H:%M:%S"`" DEBUG: Starting new run" >> $LOGFILE
log.debug "Starting new run"
echo First some questions are asked that are the same for ALL products.

# Get the image server username and password
#get_string "Image server username";
#img_serv_username=$RETURN_VALUE
##echo Chosen username for image server: $img_serv_username
#get_string "Password for image server";
#img_serv_password=$RETURN_VALUE

# Get the Huutonet username and password
if [ -z $market_username ]; then
  get_string "Huutonet username?";
  market_username=$RETURN_VALUE
fi
if [ -z $market_password ]; then
  get_string "Huutonet password";
  market_password=$RETURN_VALUE
fi

# Get the selling type
if [ -z $SELLING_TYPE ]; then
  get_selection "Choose correct selling type" "Auction (default)" "Buy out" "Auction or buyout"
  selection_number=$RETURN_VALUE
  get_string_from_list $selection_number AUCTION BUY_NOW AUCTION_AND_BUY_NOW
  SELLING_TYPE=$RETURN_VALUE
fi
#echo Chosen selling type: $SELLING_TYPE
#export SELLING_TYPE
log.debug "Chosen selling type: $SELLING_TYPE"


# Get the expiration date for the ad
if [ -z $expiring_in_days ]; then
  read -p "In how many days should the ad be closed (default 1 day): " expiring_in_days
fi
EXPIRATION_TIME=`LANG="en_EN@euro" date -d "$expiring_in_days day" +%F'T'%H:%M:%S%:z`
#while [ ! "$?" = 0 ] || [ -z $expiring_in_days ]; do
while [ ! "$?" = 0 ]; do
  read -p "In how many days should the ad be closed (default 1 day): " expiring_in_days
  EXPIRATION_TIME=`LANG="en_EN@euro" date -d "$expiring_in_days day" +%F'T'%H:%M:%S%:z`
done
echo Closing time: $EXPIRATION_TIME
log.debug "Chosen closing time: $EXPIRATION_TIME"
log.debug "Expiring in days: $expiring_in_days day"

# Get the product condition specification
if [ -z $PRODUCT_CONDITION ]; then
  get_selection "Choose product condition" "New (default)" "As new" "Good" "Ok" "Poor"
  selection_number=$RETURN_VALUE
  get_string_from_list $selection_number NEW AS_NEW GOOD OK POOR
  PRODUCT_CONDITION=$RETURN_VALUE
fi
#echo Chosen product condition: $PRODUCT_CONDITION
log.debug "Chosen product condition: $PRODUCT_CONDITION"

# Get the location for the product
if [ -z "$PRODUCT_LOCATION" ]; then
  get_string "Product location (city)?";
  PRODUCT_LOCATION=$RETURN_VALUE
fi
#echo "Chosen location (city): $PRODUCT_LOCATION"
log.debug "Chosen product location (city): $PRODUCT_LOCATION"

# Get the delivery description
#get_string "Delivery description (Muut toimitusehdot)"
#DELIVERY_DESCRIPTION=$RETURN_VALUE
#echo $DELIVERY_DESCRIPTION
if [ -z "$DELIVERY_DESCRIPTION" ]; then
  read -p "Delivery description (Muut toimitusehdot): " DELIVERY_DESCRIPTION
fi
log.debug "Chosen delivery description: $DELIVERY_DESCRIPTION"

# Get the quantity
#get_integer_with_default_and_max_and_min "Number of items (default 1)?" 1 1 10
#QUANTITY=$RETURN_VALUE
if [ -z $QUANTITY ]; then
  QUANTITY=1
fi
log.debug "Chosen quantity: $QUANTITY"

# Get the delivery method
DELIVERY_METHOD=Posti
log.debug "Chosen delivery method: $DELIVERY_METHOD"

# Get the payment method
PAYMENT_METHOD=ACCOUNT
log.debug "Chosen payment method: $PAYMENT_METHOD"

# Get the tax percentage
if [ $VAT_PERC ]; then
  vat_perc_read=$VAT_PERC
else
  vat_perc_read=100
fi
while [ $vat_perc_read -ne 0 ] && [ $vat_perc_read -ne 23 ]; do
  get_integer_with_default_and_max_and_min "Vat percentage (default 23)?" 23 0 23
  vat_perc_read=$RETURN_VALUE
done
VAT_PERC=$vat_perc_read
#echo Vat percentage: $VAT_PERC
log.debug "Chosen vat percentage: $VAT_PERC"

# Get the shipping cost
#get_decimal "Shipping cost"
#SHIPPING_COST=$RETURN_VALUE
#echo Shipping cost: $SHIPPING_COST
#log.debug "Chosen shipping cost: $SHIPPING_COST"

# Get the category
#CATEGORY=527
if [ -z $CATEGORY ]; then
  get_decimal "Category (number)"
  CATEGORY=$RETURN_VALUE
fi
#echo Chosen category: $CATEGORY
log.debug "Chosen category: $CATEGORY"

# Start by printing some information
echo -----------------------------------------------------------------------------------
echo Now all general questions are answered and now the product specific questions come.
echo -----------------------------------------------------------------------------------
log.debug "--- Product specific section ---"


#function handle_product($PRODUCT_URL, $BATCH boolean)
function handle_product() {
  PRODUCT_URL=$1
  BATCH=$2
  # zero the shipping cost for each run (it needs to be set in the product specific section)
  SHIPPING_COST=xxx # xxx makes sure that listing fails if not set in the product specific section
  #echo "url = $PRODUCT_URL, batch = $BATCH"

  #Skip rows starting with #
  echo "$PRODUCT_URL" | grep -q "^#"
  if [ $? = 0 ]; then
    #echo "kommentar rad"
    return
  fi

  echo "$PRODUCT_URL" | grep -q "http://www.fk-shop.de"
  if [ $? = 0 ]; then
    # Ok, this one is known, so we can use an existing script to fetch this information
    log.debug Known provider, using script to fetch the product information
    . ./fetch_product_from_fk.sh
  else
    if [[ $BATCH == "true" ]]; then # Allow only one image for non-html
      echo "Cannot handle manual products in batch mode"
      log.error "Cannot handle manual products in batch mode"
      return
    fi
    echo Unknown provider, asking product specific questions manually
    . ./enter_product_info_manually.sh
  fi

  log.debug "Chosen product title: $PRODUCT_TITLE"
  log.debug "Chosen product specification html: $PRODUCT_SPECIFICATION_HTML"
  log.debug "Chosen product specification: $PRODUCT_SPECIFICATION"
  log.debug "Chosen product description: $PRODUCT_DESCRIPTION"
  log.debug "Chosen image html: $IMAGE_HTML"
  log.debug "Chosen price xml: $PRICE_XML"
  log.debug "Chosen first image file: $FIRST_IMAGE_FILE"

  generate_xml
  #echo -e "Generated xml:\n$FINAL_XML"
  ad_xml_filename="ad.xml"
  echo -e "$FINAL_XML" > $ad_xml_filename
  log.debug "Full xml generated: $FINAL_XML"

  # List this ad to the market
  list_ad_to_market "$ad_xml_filename" "$market_username" "$market_password" "$PRODUCT_URL"
}

if [ -z $PRODUCT_FILE_LIST ]; then
  read -p "Read products url's from files (empty for manual): " PRODUCT_FILE_LIST
fi
if [ ! -z "$PRODUCT_FILE_LIST" ]; then
  # dumb way to go through the rows in the file, but I ran across a problem when doing it differently (ssh sellstar.fi "mkdir -p $img_serv_dir")
  for file_row in `cat $PRODUCT_FILE_LIST | sed 's/ /fffggghhh/g'`
  do
    #echo $file_row
    handle_product "`echo $file_row | sed 's/fffggghhh/ /g'`" true
  done < "$PRODUCT_FILE_LIST"
  #while read file_row; do
  #  echo bbb $PRODUCT_FILE_LIST
  #  echo $file_row
  #  echo ccc $PRODUCT_FILE_LIST
  #  handle_product "$file_row" true
  #  echo ddd $PRODUCT_FILE_LIST
  #done < "$PRODUCT_FILE_LIST"
else
  read -p "Product url (empty to stop): " var #http://www.fk-shop.de/en/Gifts-for-Women/Other-Gift/Fun-Games/Miscellaneous/Electric-garage-door-m-guide-rail-oxid.html => perään "?actpicid=x
  while true; do
    if [ -z "$var" ]; then
      break;
    fi
    handle_product "$var" false
    read -p "Product url (empty to stop): " var #http://www.fk-shop.de/en/Gifts-for-Women/Other-Gift/Fun-Games/Miscellaneous/Electric-garage-door-m-guide-rail-oxid.html => perään "?actpicid=x
  done
fi
echo "Stopping run"
log.debug "Stopping run"
