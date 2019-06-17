#!/bin/bash
#
# Script for entering product information manually.
#
# Copyright 2010 QD-Tech Oy
# Author: John Bergbom

. ./functions

# Get the product title
read -p "Product title: " PRODUCT_TITLE
#export PRODUCT_TITLE

# Get the price
if [ $SELLING_TYPE = 'AUCTION' ]; then
  #read -p "Price: " starting_price
  #current_price=$starting_price
  #read -p "Minimum bid increase: " min_bid_increase
  get_decimal "Price"
  starting_price=$RETURN_VALUE
  current_price=$starting_price
  get_decimal "Minimum bid increase"
  min_bid_increase=$RETURN_VALUE
  PRICE_XML="<somt:currentPrice currency=\"EUR\">$current_price</somt:currentPrice>\n<huuto:startingPrice currency=\"EUR\">\
$starting_price</huuto:startingPrice><huuto:minimumIncrease currency=\"EUR\">$min_bid_increase</huuto:minimumIncrease>"
elif [ $SELLING_TYPE = 'BUY_NOW' ]; then
  #read -p "Buy now price: " buy_now_price
  get_decimal "Buy now price"
  buy_now_price=$RETURN_VALUE
  PRICE_XML="<huuto:buyNowPrice currency=\"EUR\">$buy_now_price</huuto:buyNowPrice>"
else #elif [ $SELLING_TYPE = 'AUCTION_AND_BUY_NOW' ]; then
  #read -p "Price: " starting_price
  #current_price=$starting_price
  #read -p "Minimum bid increase: " min_bid_increase
  #read -p "Buy now price: " buy_now_price
  get_decimal "Price"
  starting_price=$RETURN_VALUE
  current_price=$starting_price
  get_decimal "Minimum bid increase"
  min_bid_increase=$RETURN_VALUE
  buy_now_price=`echo "$starting_price + $min_bid_increase + 0.05" | bc`
  PRICE_XML="<somt:currentPrice currency=\"EUR\">$current_price</somt:currentPrice>\n<huuto:startingPrice currency=\"EUR\">\
$starting_price</huuto:startingPrice><huuto:minimumIncrease currency=\"EUR\">$min_bid_increase</huuto:minimumIncrease>\
<huuto:buyNowPrice currency=\"EUR\">$buy_now_price</huuto:buyNowPrice>"
fi
log.debug "Starting price: $starting_price"
log.debug "Current price: $current_price"
log.debug "Min bid increase: $min_bid_increase"
log.debug "Buy now price: $buy_now_price"
#export PRICE_XML

# Get the product specification
#read -p "Product specification: " PRODUCT_SPECIFICATION
#export PRODUCT_SPECIFICATION
#PRODUCT_SPECIFICATION="Specification of the product"
#PRODUCT_SPECIFICATION_HTML="<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>Specification of the product</font></td></tr>"
PRODUCT_SPECIFICATION=
while true; do
  read -p "Product specification (empty to stop): " spec_row
  if [ ! -z "$spec_row" ]; then
    if [ ! -z "$PRODUCT_SPECIFICATION" ]; then
      PRODUCT_SPECIFICATION=$PRODUCT_SPECIFICATION"\n"
    fi
    PRODUCT_SPECIFICATION=$PRODUCT_SPECIFICATION"`echo $spec_row`"
    PRODUCT_SPECIFICATION_HTML=$PRODUCT_SPECIFICATION_HTML"<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>"`echo $spec_row`"</font></td></tr>"
  else
    break;
  fi
done

# Get the product description
read -p "Product description: " PRODUCT_DESCRIPTION
#export PRODUCT_DESCRIPTION

# Create a directory for the images at the image server
create_dir_at_image_server

# Fetch the images, put them to the image server and create html for displaying the images
IMAGE_HTML=
mkdir -p $IMG_DIR
#cd $IMG_DIR
nbr_images_added=0
while true; do
  prompt=`echo "Enter url for image number "``expr $nbr_images_added + 1`" (empty to stop adding images): "
  #echo $prompt
  read -p "$prompt" img_url
  if [ -z $img_url ]; then
    break;
  fi
  handle_single_image $img_url $img_serv_dir $nbr_images_added $img_serv_url
  nbr_images_added=`expr $nbr_images_added + 1`
  if [[ $USE_HTML_OUTPUT != "true" ]] && [[ $nbr_images_added -eq 1 ]]; then # Allow only one image for non-html
    break;
  fi
done
#cd -
