#!/bin/bash
#
# Script for fetching product information from www.fk-shop.de
#
# Copyright 2010 QD-Tech Oy
# Author: John Bergbom

# Get the product information from the manufacturer's page
#read -p "Product url: " PRODUCT_URL #http://www.fk-shop.de/en/Gifts-for-Women/Other-Gift/Fun-Games/Miscellaneous/Electric-garage-door-m-guide-rail-oxid.html => perään "?actpicid=x
#PRODUCT_URL=http://www.fk-shop.de/en/Gifts-for-Women/Other-Gift/Fun-Games/Miscellaneous/Electric-garage-door-m-guide-rail-oxid.html
#PRODUCT_URL=http://www.fk-shop.de/en/Lighting-Tuning/Headlight/Accessories-Headlights-right-fit-for-Opel-Vauxhall-Astra-G-Yr-01-04.html
#PRODUCT_URL=http://www.fk-shop.de/en/Wheel-Spacers/Wheel-adapter/adapter-plate-1-plate-system-20-mm-fit-for-Audi-80-Typ-B4.html
wget $PRODUCT_URL -O fetched_product_page.html >> $LOGFILE 2>&1
#nbr_matches=`grep test_MorePics_ fetched_product_page.html |wc -l`
PRODUCT_TITLE=`grep test_detailsHeader fetched_product_page.html | sed 's/.*h4 big">//'|sed 's/<.*//'`
echo Product title: $PRODUCT_TITLE
#export PRODUCT_TITLE
PRODUCT_DESCRIPTION=`grep test_product_longdesc fetched_product_page.html | sed 's/.*test_product_longdesc//'|sed 's/<\/div>//' | sed 's/^[ |"|>]*//'`
#echo Product description: $PRODUCT_DESCRIPTION
log.debug "Product description: $PRODUCT_DESCRIPTION"
#export PRODUCT_DESCRIPTION
PRODUCT_PRICE=`grep test_product_price fetched_product_page.html | grep -v test_product_price_unit | sed 's/^.*value="//'|sed 's/".*//' | sed 's/,/./'`
echo Product price according to web page: $PRODUCT_PRICE

# Adjust the shipping cost using some heuristics
prod_price_int=`echo $PRODUCT_PRICE | sed 's/\..*//'`
if [[ $prod_price_int -le 200 ]]; then
  SHIPPING_COST=15
elif [[ $prod_price_int -le 500 ]]; then
  SHIPPING_COST=30
else
  SHIPPING_COST=300
fi
log.debug "Chosen shipping cost: $SHIPPING_COST"

# Get the price
if [ $SELLING_TYPE = 'AUCTION' ]; then
  starting_price=$PRODUCT_PRICE
  current_price=$starting_price
  min_bid_increase=0.1
  PRICE_XML="<somt:currentPrice currency=\"EUR\">$current_price</somt:currentPrice>\n<huuto:startingPrice currency=\"EUR\">\
$starting_price</huuto:startingPrice><huuto:minimumIncrease currency=\"EUR\">$min_bid_increase</huuto:minimumIncrease>"
elif [ $SELLING_TYPE = 'BUY_NOW' ]; then
  buy_now_price=$PRODUCT_PRICE
  PRICE_XML="<huuto:buyNowPrice currency=\"EUR\">$buy_now_price</huuto:buyNowPrice>"
else #elif [ $SELLING_TYPE = 'AUCTION_AND_BUY_NOW' ]; then
  starting_price=$PRODUCT_PRICE
  current_price=$starting_price
  min_bid_increase=0.05
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

# Create a directory for the images at the image server
create_dir_at_image_server

# Fetch the images, put them to the image server and create html for displaying the images
IMAGE_HTML=
mkdir -p $IMG_DIR
#cd $IMG_DIR
nbr_images_added=0
echo Handling images for product
for var in `grep test_MorePics_ fetched_product_page.html`
#for var in "onclick=\"oxid.image('product_img','http://www.fk-shop.de/out/pictures/7/fk_garagentorantrieb_fkto2511_07.jpg');return"
do
  #if [[ $var =~ "*" ]] ; then
  echo $var | grep -q onclick
  if [ $? = 0 ] ; then
    #echo $var
    img_url=`echo $var | sed "s/^onclick=\"oxid.image('product_img','//"|sed "s/');return//"`
    handle_single_image $img_url $img_serv_dir $nbr_images_added $img_serv_url
    nbr_images_added=`expr $nbr_images_added + 1`
    if [[ $USE_HTML_OUTPUT != "true" ]] && [[ $nbr_images_added -eq 1 ]]; then # Allow only one image for non-html
      break;
    fi
  fi
done
echo Done handling images for product
#cd -

# Get the product specification
PRODUCT_SPECIFICATION=
PRODUCT_SPECIFICATION_HTML=
for var in `egrep -A 30 "test_product_shortdesc" fetched_product_page.html | sed 's/ /fffggghhh/g' | grep -v test_product_shortdesc`
do
  row=`echo $var | sed 's/fffggghhh/ /g' | sed 's/ +//'`
  echo $row | grep -q "^<li>"
  if [ $? -eq 0 ]; then
    row2=`echo $row | sed 's/^<li>//' | sed 's/<\/li>.*//'`
    #echo $row2
    if [ ! -z "$PRODUCT_SPECIFICATION" ]; then
      PRODUCT_SPECIFICATION=$PRODUCT_SPECIFICATION"\n"
    fi
    PRODUCT_SPECIFICATION=$PRODUCT_SPECIFICATION"`echo $row2`"
    PRODUCT_SPECIFICATION_HTML=$PRODUCT_SPECIFICATION_HTML"<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>"`echo $row2`"</font></td></tr>"
  else
    break;
  fi
  #row=`echo $var | sed 's/fffggghhh/ /g' | sed 's/.*Weight/Weight/' | sed 's/).*//'`
done
if [ ! -z "$PRODUCT_SPECIFICATION" ]; then
  PRODUCT_SPECIFICATION="SHORT INFO:\n$PRODUCT_SPECIFICATION"
fi
if [ ! -z "$PRODUCT_SPECIFICATION_HTML" ]; then
  PRODUCT_SPECIFICATION_HTML="<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>SHORT INFO:</font></td></tr>$PRODUCT_SPECIFICATION_HTML"
fi

# Add the weight to the specification
MORE_SPECS=
for var in `egrep -A 1 "test_product_weight.*pperunit" fetched_product_page.html | sed 's/ /fffggghhh/g' | grep -v test_product_weight`
do
  #echo $var | grep -q test_attrTitle_
  weight=`echo $var | sed 's/fffggghhh/ /g' | sed 's/.*Weight/Weight/' | sed 's/).*//'`
  if [ ! -z "$MORE_SPECS" ]; then
    MORE_SPECS=$MORE_SPECS"\n"
  fi
  MORE_SPECS=$MORE_SPECS"`echo $weight`"
  MORE_SPECS_HTML=$MORE_SPECS_HTML"<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>"`echo $weight`"</font></td></tr>"
  break
done

#Add the extra specification information in the second sheet
#for var in `egrep "test_attrTitle_|test_attrValue_" fetched_product_page.html`
#for var in "`egrep "test_attrTitle_|test_attrValue_" fetched_product_page.html`"
#for var in "`egrep -A 1 "test_attrTitle_|test_attrValue_" fetched_product_page.html | tr -d '\n'`"
for var in `egrep "test_attrTitle_|test_attrValue_" fetched_product_page.html | sed 's/ /fffggghhh/g'`
do
  echo $var | grep -q test_attrTitle_
  if [ $? = 0 ] ; then
    fixed=`echo $var | sed 's/fffggghhh/ /g'`
    #echo fixed: $fixed
    headline="`echo -n $fixed | sed 's/.*test_attrTitle_//' | sed 's/.*"><b>//' | sed 's/<\/b.*//'`"
    #echo Headline: $headline
    #MORE_SPECS=$MORE_SPECS"`echo -n $var | sed 's/.*test_attrTitle_//' | sed 's/.*"><b>//' | sed 's/<\/b.*//'`"
    if [ ! -z "$MORE_SPECS" ]; then
      MORE_SPECS=$MORE_SPECS"\n"
    fi
    MORE_SPECS=$MORE_SPECS"`echo $headline`: "
    MORE_SPECS_HTML=$MORE_SPECS_HTML"<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>""`echo $headline`: "
  fi
  echo $var | grep -q test_attrValue_
  if [ $? = 0 ] ; then
    fixed=`echo $var | sed 's/fffggghhh/ /g'`
    #echo fixed2: $fixed
    headline="`echo -n $fixed | sed 's/.*test_attrValue_//' | sed 's/.*">//' | sed 's/<\/.*//'`"
    #echo Headline: $headline
    #MORE_SPECS=$MORE_SPECS" `echo $var | sed 's/.*test_attrValue_//' | sed 's/.*">//' | sed 's/<\/.*//'`""`echo -e '\n'`"
    MORE_SPECS=$MORE_SPECS"`echo ${headline}`"
    MORE_SPECS_HTML=$MORE_SPECS_HTML"`echo ${headline}`""</font></td></tr>"
    #echo -e -n ööö $headline \n
    #echo -e -n "$headline\n"
  fi
done
if [ ! -z "$MORE_SPECS" ]; then
  PRODUCT_SPECIFICATION="${PRODUCT_SPECIFICATION}\nEXTRA INFO:\n${MORE_SPECS}"
fi
if [ ! -z "$MORE_SPECS_HTML" ]; then
  PRODUCT_SPECIFICATION_HTML="${PRODUCT_SPECIFICATION_HTML}\n<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>EXTRA INFO:</font></td></tr>$MORE_SPECS_HTML"
fi

#echo $PRODUCT_SPECIFICATION | sed 's/nnnnnn/\n/'
#PRODUCT_SPECIFICATION=`echo $PRODUCT_SPECIFICATION | sed 's/nnnnnn/\n/g'`
#echo -e Specification: $PRODUCT_SPECIFICATION # | sed 's/nnnnnn/\n/g' | grep -v ^$
#echo -e Specification: $PRODUCT_SPECIFICATION | grep -v ^$
#echo -e Specification-HTML: $PRODUCT_SPECIFICATION_HTML
log.debug "Specification: $PRODUCT_SPECIFICATION"
#echo "Specification: $PRODUCT_SPECIFICATION"
log.debug "Specification-HTML: $PRODUCT_SPECIFICATION_HTML"
#export PRODUCT_SPECIFICATION
#export PRODUCT_SPECIFICATION_HTML
