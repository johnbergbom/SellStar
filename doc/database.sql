-- Create clauses for the database
CREATE TABLE currency (
	id int8 NOT null,
	name varchar(16) not null,
	symbol varchar(16) not null,
	exrate_euro numeric(12,6) not null,
	last_updated timestamp without time zone not null
);
CREATE SEQUENCE currency_id_seq;
ALTER TABLE ONLY currency ADD CONSTRAINT curr_pkey PRIMARY KEY (id);
CREATE TABLE verification (
	id int8 NOT null,
	vat_perc numeric(12,6) not null,
	currency_id int8 not null,
	tot_sp_inc_vat numeric(12,6) not null,
	tot_sp_exc_vat numeric(12,6) not null,
	tot_sp_inc_vat_corr numeric(12,6) not null,
	tot_sp_exc_vat_corr numeric(12,6) not null
);
CREATE SEQUENCE verification_id_seq;
ALTER TABLE ONLY verification ADD CONSTRAINT verif_pkey PRIMARY KEY (id);
ALTER TABLE verification
    ADD CONSTRAINT verif_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
ALTER TABLE verification ADD CONSTRAINT perc_curr_id_key UNIQUE (vat_perc, currency_id);
CREATE TABLE users (
	id int8 NOT null,
	name varchar(64) not null,
	login_name varchar(16) not null,
	password_md5 varchar(64) not null,
	operator boolean not null,
	email_address varchar(64) not null,
	fwd_bugs boolean not null,
	fwd_glob_err boolean not null,
	fwd_glob_oper_pr boolean not null,
	fwd_oper_unkn_em boolean not null
);
CREATE SEQUENCE users_id_seq;
ALTER TABLE ONLY users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE users ADD CONSTRAINT users_login_name_key UNIQUE (login_name);
ALTER TABLE users ADD CONSTRAINT users_address_key UNIQUE (email_address);
CREATE TABLE system_email (
	id int8 NOT null,
	email_address varchar(64) not null,
	email_password varchar(64) not null,
	email_inbound_protocol varchar(64) not null,
	email_outbound_protocol varchar(64) not null,
	email_inbound_port integer not null,
	email_outbound_port integer not null,
	email_inbound_ssl boolean not null,
	email_inbound_host varchar(64) not null,
	email_outbound_host varchar(64) not null
	name varchar(64) not null,
	check_email boolean not null
);
CREATE SEQUENCE system_email_id_seq;
ALTER TABLE ONLY system_email ADD CONSTRAINT system_email_pkey PRIMARY KEY (id);
ALTER TABLE system_email ADD CONSTRAINT system_email_address_key UNIQUE (email_address);
CREATE TABLE seller (
	id int8 NOT null,
	name varchar(64) not null,
	signature varchar(64) not null,
	system_email_id int8 not null,
	user_id int8 not null,
	fwd_public_quest varchar(128),
	fwd_private_quest varchar(128),
	fwd_unknown_emails varchar(128),
	fwd_customer_emails varchar(128),
	fwd_error_reports varchar(128),
	fwd_oper_problems varchar(128),
	store_url varchar(128) not null,
	margin numeric(12,6) not null,
	ship_cost_multiplier numeric(12,6) not null,
	tracking_always boolean not null,
	delivery_text varchar(4096) not null,
	customer_vat_perc numeric(12,6) not null
);
CREATE SEQUENCE seller_id_seq;
ALTER TABLE ONLY seller ADD CONSTRAINT seller_pkey PRIMARY KEY (id);
ALTER TABLE seller ADD CONSTRAINT seller_signature_key UNIQUE (signature);
ALTER TABLE seller
    ADD CONSTRAINT sell_use_fk
    FOREIGN KEY (user_id)
    REFERENCES users;
ALTER TABLE seller
    ADD CONSTRAINT sell_se_fk
    FOREIGN KEY (system_email_id)
    REFERENCES system_email;
CREATE TABLE message (
	id int8 NOT null,
	key varchar(255) not null,
	seller_id int8,
	language varchar(16) not null,
	text varchar(4096) not null
);
CREATE SEQUENCE message_id_seq;
ALTER TABLE ONLY message ADD CONSTRAINT message_pkey PRIMARY KEY (id);
ALTER TABLE message ADD CONSTRAINT mes_ksl_key UNIQUE (key, seller_id, language);
ALTER TABLE message
    ADD CONSTRAINT mes_sel_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
CREATE TABLE translation (
	id int8 NOT null,
	original_text varchar(255) not null,
	translated_text varchar(255) not null,
	language varchar(255) not null,
	seller_id int8 not null
);
ALTER TABLE translation
    ADD CONSTRAINT tra_sel_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
CREATE SEQUENCE translation_id_seq;
ALTER TABLE ONLY translation ADD CONSTRAINT translation_pkey PRIMARY KEY (id);
CREATE TABLE provider (
	id int8 NOT null,
	currency_id int8 not null,
	name varchar(255) not null,
	url varchar(255) not null,
	username varchar(32) not null,
	password varchar(32) not null,
	system_email_id int8 not null,
	bundle_support boolean not null,
	location varchar(32) not null,
	vat_perc numeric(12,6) not null
);
CREATE SEQUENCE provider_id_seq;
ALTER TABLE ONLY provider ADD CONSTRAINT provider_pkey PRIMARY KEY (id);
ALTER TABLE provider ADD CONSTRAINT provider_name_key UNIQUE (name);
ALTER TABLE provider
    ADD CONSTRAINT prov_se_fk
    FOREIGN KEY (system_email_id)
    REFERENCES system_email;
ALTER TABLE provider
    ADD CONSTRAINT prov_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
CREATE TABLE market (
	id int8 NOT null,
	currency_id int8 not null,
	name varchar(255) not null,
	url varchar(255) not null,
	language varchar(16) not null
);
CREATE SEQUENCE market_id_seq;
ALTER TABLE ONLY market ADD CONSTRAINT market_pkey PRIMARY KEY (id);
ALTER TABLE market ADD CONSTRAINT market_name_key UNIQUE (name);
ALTER TABLE market
    ADD CONSTRAINT mar_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
CREATE TABLE seller_market (
	id int8 NOT null,
	seller_id int8 not null,
	market_id int8 not null,
	user_name varchar(64) not null,
	password varchar(64) not null,
	ad_decorator varchar(64) not null,
	min_ship_cost_mark_curr_inc_vat numeric(12,6) not null,
	min_bid_increase_mc_inc_vat numeric(12,6) not null,
	max_bundle_size_mc_exc_vat numeric(12,6) not null,
	do_listing boolean not null,
	buyout_multiplier numeric(12,6) not null
);
CREATE SEQUENCE seller_market_id_seq;
ALTER TABLE ONLY seller_market ADD CONSTRAINT seller_market_pkey PRIMARY KEY (id);
ALTER TABLE seller_market
    ADD CONSTRAINT sm_sel_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
ALTER TABLE seller_market
    ADD CONSTRAINT sm_mar_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE seller_market ADD CONSTRAINT sm_sm_id_key UNIQUE (seller_id, market_id);
ALTER TABLE seller_market ADD CONSTRAINT mu_sm_id_key UNIQUE (market_id, user_name);
CREATE TABLE category (
	id int8 NOT null,
	name varchar(255) not null,
	parent_category_id int8,
	market_spec_id integer not null,
	market_id int8 not null
);
ALTER TABLE ONLY category ADD CONSTRAINT category_pkey PRIMARY KEY (id);
CREATE SEQUENCE category_id_seq;
ALTER TABLE category
    ADD CONSTRAINT cat_cat_fk
    FOREIGN KEY (parent_category_id)
    REFERENCES category;
ALTER TABLE category
    ADD CONSTRAINT cat_mark_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE category ADD CONSTRAINT name_par_id_key UNIQUE (name, parent_category_id);
CREATE TABLE category_mapping (
	id int8 NOT null,
	category_id1 int8 not null,
	category_id2 int8 not null
);
ALTER TABLE ONLY category_mapping ADD CONSTRAINT category_map_pkey PRIMARY KEY (id);
CREATE SEQUENCE category_map_id_seq;
ALTER TABLE category_mapping
    ADD CONSTRAINT map_cato_fk
    FOREIGN KEY (category_id1)
    REFERENCES category;
ALTER TABLE category_mapping
    ADD CONSTRAINT map_catt_fk
    FOREIGN KEY (category_id2)
    REFERENCES category;
ALTER TABLE category_mapping ADD CONSTRAINT cato_catt_id_key UNIQUE (category_id1, category_id2);
CREATE TABLE product (
	id int8 NOT null,
	created_timestamp timestamp without time zone not null,
	name varchar(255) not null,
	provider_prod_id varchar(255) not null,
	product_link_id int8,
	provider_id int8 not null,
	provider_category varchar(255) not null,
	technical_specs text not null,
	category_id int8,
	dimensions text,
	contents text,
	cat_suggestion varchar(1024),
	compensation boolean not null,
	provider_url varchar(255) not null,
	min_prov_ship_cost_pc_inc_vat numeric(12,6) not null,
	status_fetch_date timestamp without time zone,
	curr_prov_price_pc_inc_vat numeric(12,6),
	product_state integer not null,
	not_found_counter integer not null,
	manufacturer_url varchar(255),
	offline_sale boolean not null
);
CREATE SEQUENCE product_id_seq;
ALTER TABLE ONLY product ADD CONSTRAINT product_pkey PRIMARY KEY (id);
ALTER TABLE product ADD CONSTRAINT prov_prod_id_key UNIQUE (provider_prod_id, provider_id);
ALTER TABLE product ADD CONSTRAINT product_url_key UNIQUE (provider_url);
ALTER TABLE product ADD CONSTRAINT manufacturer_url_key UNIQUE (manufacturer_url);
ALTER TABLE product
    ADD CONSTRAINT prod_prov_fk
    FOREIGN KEY (provider_id)
    REFERENCES provider;
ALTER TABLE product
    ADD CONSTRAINT prod_prod_fk
    FOREIGN KEY (product_link_id)
    REFERENCES product;
ALTER TABLE product
    ADD CONSTRAINT prod_cat_fk
    FOREIGN KEY (category_id)
    REFERENCES category;
CREATE TABLE product_keywords (
	id int8 NOT null,
	keyword varchar(255) not null,
	product_id int8 not null
);
CREATE SEQUENCE product_keyword_id_seq;
ALTER TABLE ONLY product_keywords ADD CONSTRAINT product_keyword_pkey PRIMARY KEY (id);
ALTER TABLE product_keywords
    ADD CONSTRAINT pk_prod_fk
    FOREIGN KEY (product_id)
    REFERENCES product;
CREATE TABLE adtemplate (
	id int8 NOT null,
	product_id int8 not null,
	active boolean not null,
	bean_name varchar(255) not null,
	quantity integer not null,
	headline varchar(255) not null,
	open_days integer not null,
	location varchar(255) not null,
	classification integer,
	payment_way integer not null,
	shipping_way integer not null,
	shipping_desc varchar(255) not null,
	summary text not null,
	technical_specs text,
	details text not null,
	slogan varchar(255) not null,
	added_by varchar(255) not null,
	created_timestamp timestamp without time zone not null,
	seller_id int8 not null,
	language varchar(16) not null,
	visibility_type integer not null,
	inactive_reason varchar(255)
);
CREATE SEQUENCE adtemplate_id_seq;
ALTER TABLE ONLY adtemplate ADD CONSTRAINT adtemplate_pkey PRIMARY KEY (id);
ALTER TABLE adtemplate
    ADD CONSTRAINT adt_prod_fk
    FOREIGN KEY (product_id)
    REFERENCES product;
ALTER TABLE adtemplate
    ADD CONSTRAINT adt_sell_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
CREATE TABLE ad (
	id int8 NOT null,
	date_from_market timestamp without time zone,
	date_to_market timestamp without time zone not null,
	date_closing timestamp without time zone not null,
	market_sales_id varchar(255) not null,
	prov_price_at_listing_euro_inc_vat numeric(12,6) not null,
	prov_price_at_listing_pc_inc_vat numeric(12,6) not null,
	start_price_euro_inc_vat numeric(12,6) not null,
	start_price_mc_inc_vat numeric(12,6) not null,
	min_selling_price_euro_inc_vat numeric(12,6) not null,
	min_selling_price_mc_inc_vat numeric(12,6) not null,
	used_bean_name varchar(255) NOT null,
	used_bean_version varchar(255) NOT null,
	market_id int8 NOT null,
	ad_template_id int8 NOT null,
	confirmed boolean not null,
	shipping_cost_euro numeric(12,6) not null,
	shipping_cost_mc numeric(12,6) not null,
	buyout_price_mc_inc_vat numeric(12,6) not null,
	buyout_price_euro_inc_vat numeric(12,6) not null,
	sales_type integer not null,
	market_category_id int8 not null,
	nbr_public_questions integer not null,
	original_amount integer not null,
	bought_amount integer not null,
	original_ad_id int8,
	visibility_type integer not null,
	open_days integer not null,
	advert_cost_euro_inc_vat numeric(12,6) not null,
	advert_cost_euro_exc_vat numeric(12,6) not null,
	transfered_to_verific boolean not null
);
CREATE SEQUENCE ad_id_seq;
ALTER TABLE ONLY ad ADD CONSTRAINT ad_pkey PRIMARY KEY (id);
ALTER TABLE ad
    ADD CONSTRAINT ad_mark_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE ad
    ADD CONSTRAINT ad_templ_fk
    FOREIGN KEY (ad_template_id)
    REFERENCES adtemplate;
ALTER TABLE ad
    ADD CONSTRAINT ad_cat_fk
    FOREIGN KEY (market_category_id)
    REFERENCES category;
ALTER TABLE ad
    ADD CONSTRAINT ad_ad_fk
    FOREIGN KEY (original_ad_id)
    REFERENCES ad;
ALTER TABLE ad ADD CONSTRAINT ad_market_sales_id_key UNIQUE (market_sales_id, market_id);
CREATE TABLE customer (
	id int8 NOT null,
	email varchar(255) not null,
	phone_number varchar(255),
	date_kicked timestamp without time zone not null,
	nbr_times_kicked integer not null,
	informed_too_big_pay boolean not null,
	conf_code varchar(255) not null,
	account_no varchar(255)
);
ALTER TABLE ONLY customer ADD CONSTRAINT customer_pkey PRIMARY KEY (id);
ALTER TABLE customer ADD CONSTRAINT customer_email_id_key UNIQUE (email);
ALTER TABLE customer ADD CONSTRAINT cust_conf_code_key UNIQUE (conf_code);
CREATE SEQUENCE customer_id_seq;
CREATE TABLE login_name (
	id int8 NOT null,
	customer_id int8 not null,
	market_id int8 not null,
	login_name varchar(255) not null
);
ALTER TABLE ONLY login_name ADD CONSTRAINT login_name_pkey PRIMARY KEY (id);
ALTER TABLE login_name ADD CONSTRAINT lg_lgm_key UNIQUE (customer_id, market_id);
ALTER TABLE login_name ADD CONSTRAINT lg_malo_key UNIQUE (market_id, login_name);
ALTER TABLE login_name
    ADD CONSTRAINT lg_c_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
ALTER TABLE login_name
    ADD CONSTRAINT lg_m_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
CREATE SEQUENCE login_name_id_seq;
CREATE TABLE order_bundle (
	id int8 NOT null,
	prov_order_id varchar(255),
	transaction_id varchar(255),
	customer_id int8 not null,
	provider_id int8 not null,
	seller_id int8 not null,
	last_cust_update_sent timestamp without time zone not null,
	first_name varchar(255) not null,
	last_name varchar(255) not null,
	address varchar(255) not null,
	postcode varchar(255) not null,
	city varchar(255) not null,
	cust_shipping_cost_mc_inc_vat numeric(12,6) not null,
	cust_shipping_cost_mc_exc_vat numeric(12,6) not null,
	cust_shipping_cost_euro_inc_vat numeric(12,6) not null,
	cust_shipping_cost_euro_exc_vat numeric(12,6) not null,
	targeted_ship_mc numeric(12,6) not null,
	prov_ship_pc_inc_vat numeric(12,6),
	prov_ship_pc_exc_vat numeric(12,6),
	prov_ship_euro_inc_vat numeric(12,6),
	prov_ship_euro_exc_vat numeric(12,6),
	paypal_id varchar(255),
	resend_loss_euro_inc_vat numeric(12,6) not null,
	resend_loss_euro_exc_vat numeric(12,6) not null,
	pcurrency_id int8 not null,
	ccurrency_id int8 not null,
	max_bundle_size_mc_exc_vat numeric(12,6) not null,
	customer_ship_vat_perc numeric(12,6) not null,
	provider_ship_vat_perc numeric(12,6),
	provider_ship_resend_vat_perc numeric(12,6),
	transfered_to_verific boolean not null,
	old_verification boolean not null,
	comment varchar(512)
);
ALTER TABLE ONLY order_bundle ADD CONSTRAINT order_bundle_pkey PRIMARY KEY (id);
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_cust_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
ALTER TABLE order_bundle ADD CONSTRAINT prov_ordbund_id_key UNIQUE (prov_order_id, provider_id);
ALTER TABLE order_bundle ADD CONSTRAINT tran_ordbund_id_key UNIQUE (transaction_id, provider_id);
ALTER TABLE order_bundle ADD CONSTRAINT pp_ordbund_id_key UNIQUE (paypal_id, provider_id);
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_prov_fk
    FOREIGN KEY (provider_id)
    REFERENCES provider;
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_sell_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_pcur_fk
    FOREIGN KEY (pcurrency_id)
    REFERENCES currency;
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_ccur_fk
    FOREIGN KEY (ccurrency_id)
    REFERENCES currency;
CREATE SEQUENCE order_bundle_id_seq;
CREATE TABLE tracking (
	id int8 NOT null,
 	tracking_code varchar(255) not null,
	order_bundle_id int8 not null
);
ALTER TABLE ONLY tracking ADD CONSTRAINT tracking_pkey PRIMARY KEY (id);
CREATE SEQUENCE tracking_id_seq;
ALTER TABLE tracking ADD CONSTRAINT tracking_trcode_key UNIQUE (tracking_code);
ALTER TABLE tracking
    ADD CONSTRAINT trck_ordbund_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
CREATE TABLE orders (
	id int8 NOT null,
	reference varchar(255) not null,
	fin_pp_euro_inc_vat numeric(12,6),
	fin_pp_euro_exc_vat numeric(12,6),
	fin_pp_pc_inc_vat numeric(12,6),
	fin_pp_pc_exc_vat numeric(12,6),
	fin_sp_euro_inc_vat numeric(12,6) not null,
	fin_sp_euro_exc_vat numeric(12,6) not null,
	fin_sp_mc_inc_vat numeric(12,6) not null,
	fin_sp_mc_exc_vat numeric(12,6) not null,
	state integer not null,
	ad_id int8 not null,
	feedback_given boolean not null,
	market_id int8 not null,
	send_count integer not null,
	other_loss_euro numeric(12,6) not null,
	targeted_mc numeric(12,6) not null,
	try_to_bundle boolean not null,
	order_bundle_id int8 not null,
	refunding_mc numeric(12,6) not null,
	tracking_id int8,
	resend_loss_euro_inc_vat numeric(12,6) not null,
	resend_loss_euro_exc_vat numeric(12,6) not null,
	customer_vat_perc numeric(12,6) not null,
	provider_vat_perc numeric(12,6),
	provider_resend_vat_perc numeric(12,6),
	transfered_to_verific boolean not null,
	old_verification boolean not null,
	product_id int8 not null,
	google_order boolean not null
);
ALTER TABLE ONLY orders ADD CONSTRAINT orders_pkey PRIMARY KEY (id);
ALTER TABLE orders ADD CONSTRAINT orders_reference_key UNIQUE (reference);
ALTER TABLE orders
    ADD CONSTRAINT ord_ad_fk
    FOREIGN KEY (ad_id)
    REFERENCES ad;
ALTER TABLE orders
    ADD CONSTRAINT ord_ma_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE orders
    ADD CONSTRAINT ord_ordbund_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
ALTER TABLE orders
    ADD CONSTRAINT ord_track_fk
    FOREIGN KEY (tracking_id)
    REFERENCES tracking;
CREATE SEQUENCE orders_id_seq;
CREATE TABLE provider_question (
	id int8 NOT null,
	order_bundle_id int8 not null,
	nbr_messages int not null,
	last_own_comment timestamp without time zone not null,
	last_provider_comment timestamp without time zone,
	operator_last_informed timestamp without time zone,
	open boolean not null,
	provider_ticket_id varchar(255) not null,
	subject varchar(255) not null,
	comment_to_add text,
	contents text not null
);
ALTER TABLE ONLY provider_question ADD CONSTRAINT provider_question_pkey PRIMARY KEY (id);
CREATE SEQUENCE provider_question_id_seq;
ALTER TABLE provider_question ADD CONSTRAINT provquest_provti_key UNIQUE (provider_ticket_id);
ALTER TABLE provider_question
    ADD CONSTRAINT provquest_bundle_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
CREATE TABLE state_changes (
	id int8 NOT null,
	state integer not null,
	update_date timestamp without time zone not null,
	order_id int8 NOT null
);
ALTER TABLE ONLY state_changes ADD CONSTRAINT state_changes_pkey PRIMARY KEY (id);
ALTER TABLE state_changes
    ADD CONSTRAINT sc_or_fk
    FOREIGN KEY (order_id)
    REFERENCES orders;
CREATE SEQUENCE state_change_id_seq;
CREATE TABLE image (
	id int8 NOT null,
	big_img varchar(255),
	html varchar(255),
	img_host_url varchar(255),
	image_order integer not null,
	img_text varchar(255),
	link_img varchar(255),
	link_img_desc varchar(255),
	loc_file_url varchar(255) not null,
	small_img varchar(255),
	product_id int8 NOT null,
	thumbnail_image_id int8
);
CREATE SEQUENCE image_id_seq;
ALTER TABLE ONLY image ADD CONSTRAINT image_pkey PRIMARY KEY (id);
ALTER TABLE image
    ADD CONSTRAINT img_prod_fk
    FOREIGN KEY (product_id)
    REFERENCES product;
CREATE TABLE settings (
	key varchar(255) NOT null,
	value text NOT null
);
ALTER TABLE ONLY settings ADD CONSTRAINT settings_pkey PRIMARY KEY (key);
CREATE TABLE transaction (
	id int8 NOT null,
	amount numeric(12,6) NOT null,
	date timestamp without time zone not null,
	description varchar(255) not null,
	message varchar(255) not null,
	payer varchar(255) not null,
	ref_number varchar(255) not null,
	oper_descr varchar(255),
	order_id int8,
	currency_id int8 not null,
	transfered_to_verific boolean not null,
	old_verification boolean not null
);
ALTER TABLE ONLY transaction ADD CONSTRAINT transaction_pkey PRIMARY KEY (id);
ALTER TABLE transaction
    ADD CONSTRAINT tra_ord_fk
    FOREIGN KEY (order_id)
    REFERENCES orders;
CREATE SEQUENCE transaction_id_seq;
ALTER TABLE transaction
    ADD CONSTRAINT tra_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
CREATE TABLE email (
	id int8 NOT null,
	date timestamp without time zone not null,
	system_email_id int8 not null,
	sender varchar(255) not null,
	receiver varchar(255) not null,
	subject varchar(255) not null,
	contents text not null,
	type integer not null,
	direction integer not null,
	sending_attempts integer not null,
	order_bundle_id int8,
	customer_id int8,
	priority integer not null
);
ALTER TABLE ONLY email ADD CONSTRAINT email_pkey PRIMARY KEY (id);
CREATE SEQUENCE email_id_seq;
ALTER TABLE email
    ADD CONSTRAINT emai_se_fk
    FOREIGN KEY (system_email_id)
    REFERENCES system_email;
ALTER TABLE email
    ADD CONSTRAINT em_cu_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
CREATE INDEX email_typ_index ON email (type);
CREATE INDEX email_dir_index ON email (direction);
ALTER TABLE email
    ADD CONSTRAINT email_bundle_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
CREATE INDEX email_ob_index ON email (order_bundle_id);
CREATE TABLE error_tracking (
	id int8 NOT null,
	command varchar(255) not null,
	arguments varchar(255),
	market_id int8,
	provider_id int8,
	seller_id int8,
	date timestamp without time zone not null,
	acknowledged timestamp without time zone,
	message varchar(8192) not null,
	stacktrace text not null
);
ALTER TABLE ONLY error_tracking ADD CONSTRAINT error_tracking_pkey PRIMARY KEY (id);
CREATE SEQUENCE error_tracking_id_seq;
ALTER TABLE error_tracking
    ADD CONSTRAINT et_ma_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE error_tracking
    ADD CONSTRAINT et_pr_fk
    FOREIGN KEY (provider_id)
    REFERENCES provider;
ALTER TABLE error_tracking
    ADD CONSTRAINT et_se_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;



-- Sql clauses for dropping all tables and sequences:
drop TABLE error_tracking;
drop TABLE seller_market;
drop TABLE category_mapping;
drop TABLE transaction;
drop TABLE state_changes;
drop TABLE provider_question;
drop TABLE orders;
drop TABLE tracking;
drop TABLE login_name;
drop TABLE email;
drop TABLE order_bundle;
drop TABLE customer;
drop TABLE settings;
drop TABLE ad;
drop TABLE adtemplate;
drop TABLE image;
drop TABLE product_keywords;
drop TABLE product;
drop TABLE category;
drop TABLE market;
drop TABLE provider;
drop TABLE translation;
drop TABLE message;
drop TABLE seller;
drop TABLE users;
drop TABLE system_email;
drop TABLE verification;
drop TABLE currency;
drop SEQUENCE state_change_id_seq;
drop SEQUENCE orders_id_seq;
drop SEQUENCE customer_id_seq;
drop SEQUENCE ad_id_seq;
drop SEQUENCE image_id_seq;
drop SEQUENCE market_id_seq;
drop SEQUENCE product_id_seq;
drop SEQUENCE provider_id_seq;
drop SEQUENCE transaction_id_seq;
drop SEQUENCE translation_id_seq;
drop SEQUENCE email_id_seq;
drop SEQUENCE adtemplate_id_seq;
drop SEQUENCE category_id_seq;
drop SEQUENCE category_map_id_seq;
drop SEQUENCE product_keyword_id_seq;
drop SEQUENCE login_name_id_seq;
drop SEQUENCE seller_id_seq;
drop SEQUENCE seller_market_id_seq;
drop SEQUENCE users_id_seq;
drop SEQUENCE message_id_seq;
drop SEQUENCE system_email_id_seq;
drop SEQUENCE order_bundle_id_seq;
drop SEQUENCE tracking_id_seq;
drop SEQUENCE error_tracking_id_seq;
drop SEQUENCE provider_question_id_seq;
drop SEQUENCE verification_id_seq;
drop SEQUENCE currency_id_seq;

--- For testing of email-stuff
insert into product (id,created_timestamp,name,provider_prod_id,product_link_id,provider_id) values (nextval('product_id_seq'),'now','testproduct','sku.11866',null,currval('provider_id_seq'));
-- set escape_string_warning = off;
-- insert into ad (id,description,headline,market_sales_id,quantity,shipping_cost_euro,shipping_cost_mc,start_price_euro,start_price_mc,market_id,
--   product_id,technical_specs,sales_type,open_days,min_bid_increase_euro,min_bid_increase_mc,category,sub_category,min_selling_price_euro,
--   min_selling_price_mc)
--   values (nextval('ad_id_seq'),'descr','headline','121948817',1,0,0,5,5,currval('market_id_seq'),currval('product_id_seq'),
--   'technical\nspecs',1,1,0.5,0.5,'324','333',3.0,3.0);
-- set escape_string_warning = on;

--- For testing of listing stuff to huutonet
-- insert an ad which is used as a template:
insert into ad (id,description,headline,quantity,shipping_cost_euro,shipping_cost_mc,market_id,product_id,
  sales_type,open_days,min_bid_increase_euro,min_bid_increase_mc,category,sub_category,min_selling_price_euro,
  min_selling_price_mc,technical_specs)
  values (nextval('ad_id_seq'),'Test-descr','Test-headline',1,1,1,currval('market_id_seq'),currval('product_id_seq'),
  1,1,0.5,0.5,'324','333',3.0,3.0,'technical\nspecs');
-- insert an ad template for the ad that was just added:
insert into adtemplate (adid,marketid,productid,active,bean_name) values (currval('ad_id_seq'),currval('market_id_seq'),currval('product_id_seq'),false,'baseAdCreator');

-- Providers
insert into provider (id,currency,name,url,username,password) values (nextval('provider_id_seq'),2,'DealExtreme','http://www.dealextreme.com','someemail@gmail.com','somepassword');
insert into provider (id,currency,name,url,username,password) values (nextval('provider_id_seq'),2,'FocalPrice','http://www.focalprice.com','someemail@gmail.com','somepassword');

-- Markets
insert into market (id,currency,name,url,username,password) values (nextval('market_id_seq'),1,'Huutonet','http://www.huuto.net','SomeUserName','somepass');
insert into market (id,currency,name,url,username,password) values (nextval('market_id_seq'),1,'Google-Order','http://laubertea.appspot.com','','');
insert into market (id,currency,name,url,username,password) values (nextval('market_id_seq'),1,'SellStar','http://localhost','','');
insert into market (id,currency,name,url,username,password) values (nextval('market_id_seq'),1,'Mikko','http://www.mikko.fi','SomeUserName','somepass');
insert into market (id,currency,name,url,username,password) values (nextval('market_id_seq'),1,'HuutonetSNAPSHOT','http://www.huuto.net','','');
insert into market (id,currency,name,url,username,password) values (nextval('market_id_seq'),1,'MikkoSNAPSHOT','http://www.mikko.fi','','');

-- General selenium stuff
insert into settings (key,value) values ('selenium_page_load_timeout','60000');

-- Email configuration
insert into settings (key,value) values ('mail.inbound.host','imap.gmail.com');
insert into settings (key,value) values ('mail.inbound.username','someemail@gmail.com');
insert into settings (key,value) values ('mail.inbound.password','somepassword');
insert into settings (key,value) values ('mail.inbound.store.protocol','imap');
insert into settings (key,value) values ('mail.inbound.port','993');
insert into settings (key,value) values ('mail.inbound.sslInUse','true');
insert into settings (key,value) values ('mail.outbound.protocol','smtp');
insert into settings (key,value) values ('mail.outbound.host','smtp.gmail.com');
insert into settings (key,value) values ('mail.outbound.port','465');
insert into settings (key,value) values ('mail.outbound.username','someemail@gmail.com');
insert into settings (key,value) values ('mail.outbound.password','somepassword');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.fakeSend','false');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.ForwardProcessor.subjectPrefix','Unknown email');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardOperationProblemsTo','some@email.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardErrorsTo','some@email.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardUnknownEmailsTo','some@email.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.ForwardProcessor.operators','someemail@email.fi,someemail2@gmail.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardPublicQuestionsTo','some@email.fi,someemail2@gmail.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardPrivateQuestionsTo','some@email.fi,someemail2@gmail.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.EmailFromCustomerProcessor.forwardCustomerEmailsTo','some@email.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardFaultyContactInfo','some@email.fi');
insert into settings (key,value) values ('bugReportEmailAddress','some@email.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.PaypalProcessor.paypalAddress','service@paypal.com.hk');
insert into settings (key,value) values ('fi.jonix.huutonet.order.focalprice.FocalPriceProcessor.paypalAddress','service@intl.paypal.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.PaypalProcessor.paypalRefundAddress','service@intl.paypal.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.dxAddress','sales@dealextreme.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.huutonetAddress','meklari@huuto.net');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.huutonetAddressWebMaster','webmaster@huuto.net');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.MikkoProcessor.mikkoAddress','mikko-asiakaspalvelu@mikko.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.emailOrdersAddress','someemail2@gmail.com');
set escape_string_warning = off;
insert into settings (key,value) values ('fi.jonix.huutonet.tools.BankHelper.moneyReceivedEmail','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuotteen AD_HEADLINE\nmaksu TOTAL_PRICE on nyt kirjautunut tilillemme.\n\nTuote lähetetään keskitetyn tilausjärjestelmämme seuraavan lähetyserän mukana eli noin viikon sisällä. Ilmoitamme teille myös sähköpostitse, kun tilaus lähtee postin kuljetettavaksi.\n\nTuote lähetetään seuraavin tiedoin:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithoutTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kolmen viikon sisällä.\n\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kolmen viikon sisällä. Seurantakoodi on TRACKING_CODE ja pääset muutaman päivän sisällä seuraamaan tilauksesi statusta seuraavasta osoitteesta: http://app3.hongkongpost.com/CGI/mt/genresult.jsp?tracknbr=TRACKING_CODE \n\nSuomen postijärjestelmä ei ole vielä integroitu Hong Kong postin kanssa, joten status tietoa tulee tällä hetkellä ainoastaan Hong Kongin päästä.\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmail','Kiitos tilauksestanne,\n\nTilausnumeronne on ORDER_ID AD_HEADLINE\n\nTilauksenne käsittely jatkuu, kun maksu on rekisteröitynyt tilillemme.\n\nLaskun tiedot:\n\nHandelsbanken\nMaksun saaja: Team Lauber\nTilinro: ACCOUNT_NUMBER\nViitenumero: REFERENCE_NUMBER  (HUOM! Viitenumero on pakollinen tieto)\nSumma: TOTAL_PRICE (sisältää tuotteen hinnan FINAL_SALES_PRICE ja postikulut SHIPPING_COST)\n\nMuistathan käyttää viitenumeroa, koska tilausjärjestelmämme tarkistaa laskut automaattisesti viitenumeron perusteella. Tilausjärjestelmä ilmoittaa sähköpostitse, kun rahat ovat kirjautuneet tilillemme.\n\nMikäli olet tilannut kerralla useamman tuotteen ja haluat maksaa kaiken samalla laskulla, niin se onnistuu seuraavan linkin takaa aukeavalla koontilaskun pyyntölomakkeella:  GOOGLE_ORDER_URL/bills?form&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nTuote lähetetään osoitteeseenne:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nMikäli haluatte muuttaa osoitetta, johon paketti lähetetään, voitte tehdä sen oheisen linkin kautta: GOOGLE_ORDER_URL/addresschanges?form&referenceNumber=REFERENCE_NUMBER&firstName=FIRST_NAME_URL_ENC&lastName=LAST_NAME_URL_ENC&address=CUSTOMER_ADDRESS_URL_ENC&postCode=CUSTOMER_POSTCODE_URL_ENC&city=CUSTOMER_CITY_URL_ENC&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.order.CustomerStockShortageInformer.stockShortageApology','Hei,\n\nKävipä nolosti. Varastosaldomme eivät ole olleet ajantasalla ilmoitushetkellä ja tilaamasi tuote AD_HEADLINE tilausnumerolla ORDER_ID on loppunut varastolta. Tilauksen viitenumero on REFERENCE_NUMBER.\n\nVoit peruuttaa tilauksen osoitteessa GOOGLE_ORDER_URL/emailcancels?form&referenceNumber=REFERENCE_NUMBER. Toinen vaihtoehto on jäädä odottamaan täydennystilausta. Vielä ei ole tietoa milloin täydennystilaus tulee, joten ehkä varminta on peruuttaa tilaus.\n\nTilauksen peruutuksen jälkeen lähetämme vielä peruutusvahvistuspyynnön oikeuksiesi turvaamiseksi. Peruutusvahvistuksen jälkeen tilaus on peruutettu ja parin viikon sisällä tilauksesta maksamasi rahat palautetaan peruutusvahvistuksen yhteydessä määrämällesi tilille.\n\nTäydennyseriä saapuu jatkuvasti ja niitä odotelleet tilaukset käsitellään pikaisesti. Tästä johtuen tilaus on voitu ehtiä paketoida ja antaa postin kuljetettavaksi ennen kuin peruutusvahvistus saavuttaa varaston. Tällöin peruutusta ei luonnollisesti voida enää toimeenpanna. Mikäli saat peruutusvahvistuksen jälkeen viikon sisällä viestin, että tilaus on lähetetty, niin voitte jäädä odottelemaan tilaamanne tuotteen saapumista.\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('productNoLongerForSaleAtProvider','Hei,\n\nKävipä nolosti. Ehdimme myydä huutoaikana varaston loppuun ko. tuotteen osalta ja tilaamaasi tuotetta AD_HEADLINE tilausnumerolla ORDER_ID ei ole toistaiseksi saatavilla. Tilauksen viitenumero on REFERENCE_NUMBER.\n\nPeruuta tilauksesi osoitteessa GOOGLE_ORDER_URL/emailcancels?form&referenceNumber=REFERENCE_NUMBER.\n\nTilauksen peruutuksen jälkeen lähetämme vielä peruutusvahvistuspyynnön oikeuksiesi turvaamiseksi. Peruutusvahvistuksen jälkeen tilaus on peruutettu ja parin viikon sisällä tilauksesta maksamasi rahat palautetaan peruutusvahvistuksen yhteydessä määrämällesi tilille.\n\nPahoittelut aiheuttamastamme vaivannäöstäsi. Pyrimme pitämään toiminnan kulut mahdollisimman matalina ja siirtämään aiheutunut hyöty suoraan hintoihin. Asiakkaamme nauttivat poikkeuksellisen edullisista hinnoista, mutta myös satunnaisia tuote-erien loppumisia voi tapahtua.\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.tools.BankHelper.wrongOrderState','Faulty payment received for order ORDER_ID (reference number REFERENCE_NUMBER): The order is in a faulty state (state = ORDER_STATE).\n\nThe chance that the wrong person made this payment is so small that it should hardly ever happen in practice. This could happen if somebody by mistake paid twice for the same product (maybe because he actually ordered two different products). Another possibility is that the customer paid for one order, then bought more items and ordered a common bill (koontilasku) for the other orders _before_ the payment for the first item was registered. In this case the common bill could be targeted to the item that the customer already paid.\n\nThe payment was associated with this order and the targeted payment was updated, but the order of the state was not changed. Everything is most likely correct, but you might want to make sure by checking manually.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.faultyMarketSalesIdEmail','Moi,\n\nHalusit tilata tuotteen tunnisteella MARKET_SALES_ID mutta tämä tunniste\nei löydy meidän tietokannasta. Ole hyvä ja yritä uudelleen.\n\nYstävällisin terveisin,\nTeam Lauber');
set escape_string_warning = on;
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bankAccountNumber','111111-222222');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.bank.HBBankRobot.bankLanguage','FI');
-- insert into settings (key,value) values ('fi.jonix.huutonet.robot.bank.HBBankRobot.bankLanguage','SE');
insert into settings (key,value) values ('base.path','/usr/local/huutonet/test');
insert into settings (key,value) values ('fi.jonix.huutonet.tools.DxProductHelper.consoleEncoding','UTF-8');
insert into settings (key,value) values ('fi.jonix.huutonet.tools.DxProductHelper.coverMaskFile','MaskCover.png');
insert into settings (key,value) values ('fi.jonix.huutonet.tools.DxProductHelper.maskFile','TeamLauberCover.png');
insert into settings (key,value) values ('fi.jonix.huutonet.order.focalprice.FocalPriceProvider.maskFile','TeamLauber_FocalPrice_mask.jpg');
insert into settings (key,value) values ('fi.jonix.huutonet.tools.DxProductHelper.productImagePath','productimages');
insert into settings (key,value) values ('fi.jonix.huutonet.tools.TemplateTester.templateDir','testtemplate');
insert into settings (key,value) values ('error','false');
insert into settings (key,value) values ('productionEnvironment','false');

-- Other settings
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.BaseAdCreator.priceMultiplier','1.43');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetLister.shippingPriceMultiplier','1.3');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetFeedback.positive','Kiitokset kaupoista.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescriptionRegisteredMail','Paketti lähetetään kirjattuna kirjeenä.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescriptionAirMail','Paketti lähetetään tavallisena lentopostina.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.location','Hong Kong');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescription','Keskitetyn tilaus- ja toimitusprosessimme vuoksi, tilausten käsittely maksun saapumisesta postittamiseen on noin viikko. Lähetämme tuotteen Hong Kong postin kautta lentorahtina. Hong Post postin lupaama toimitusaika pääkaupunkeihin on 4-6 postin työpäivää ja muualle 7-10 postin työpäivää. Kokemuksen mukaan lähetyksaika muodostuu kuitenkin usein tätä pidemmäksi, jonka vuoksi arviomme kokonaistoimitusajaksi on 2-4 viikkoa maksun saapumisesta.\n\nTuote läheteään huutajan huuto.net-palveluun rekisteröimään osoitteeseen.');


--- Database changes for version prod20100413
ALTER TABLE adtemplate ALTER COLUMN shipping_cost_euro DROP NOT NULL;
ALTER TABLE adtemplate ALTER COLUMN shipping_cost_mc DROP NOT NULL;
UPDATE adtemplate SET shipping_cost_euro = null, shipping_cost_mc = null;
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetLister.shippingPriceMultiplier','1.2');
ALTER TABLE ad add column shipping_cost_euro double precision;
UPDATE ad set shipping_cost_euro = 0.01;
ALTER TABLE ad alter column shipping_cost_euro set not null;
ALTER TABLE ad add column shipping_cost_mc double precision;
UPDATE ad set shipping_cost_mc = 0.01;
ALTER TABLE ad alter column shipping_cost_mc set not null;
ALTER TABLE orders add column feedback_given boolean;
UPDATE orders SET feedback_given = false;
ALTER TABLE orders alter column feedback_given set not null;
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetFeedback.positive','Kiitokset kaupoista.');
ALTER TABLE adtemplate add column added_by varchar(255);
UPDATE adtemplate SET added_by = 'Lauber';
ALTER TABLE adtemplate alter column added_by set not null;
update settings set value = 'somepass' where key = 'mail.inbound.password';
update settings set value = 'somepass' where key = 'mail.outbound.password';
update settings set value = 'somepass' where key = 'fi.jonix.huutonet.robot.lister.HuutonetLister.password';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithoutTrackingCode' or key = 'fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithoutTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kahden viikon sisällä.\n\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kahden viikon sisällä. Seurantakoodi on TRACKING_CODE ja pääset katsomaan tilauksesi statusta seuraavasta osoitteesta: http://app3.hongkongpost.com/CGI/mt/enquiry.jsp\n\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.location','Hong Kong');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescription','Keskitetyn tilaus- ja toimitusprosessimme vuoksi, tilausten käsittely maksun saapumisesta postittamiseen on muutama arkipäivä. Lähetämme tuotteen Hong Kong postin kautta lentorahtina. Hong Post postin lupaama toimitusaika pääkaupunkeihin on 4-6 postin työpäivää ja muualle 7-10 postin työpäivää. Kokonaisuudessaan toimitukseen menee noin pari viikkoa.');
update orders set state = 101 where state = 99;
alter table orders add column paid_too_much boolean;
update orders set paid_too_much = false;
alter table orders alter column paid_too_much set not null;
alter table orders add column date_kicked timestamp without time zone;
UPDATE image i SET img_host_url = substring(i.img_host_url from 0 for position('thumbs' in i.img_host_url)) || 'images' || substring(i.img_host_url from position('/teamlauber' in i.img_host_url)) WHERE i.img_host_url like '%/thumbs/teamlauber%';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescriptionRegisteredMail','Paketti lähetetään kirjattuna kirjeenä.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescriptionAirMail','Paketti lähetetään tavallisena lentopostina.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.huutonetAddressWebMaster','webmaster@huuto.net');
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardOperationProblemsTo';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardOperationProblemsTo','some@email.fi');

delete from settings where key = 'instanceRunning';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardPublicQuestionsTo','some@email.fi,someemail2@gmail.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardPrivateQuestionsTo','some@email.fi,someemail2@gmail.com');

delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithoutTrackingCode' or key = 'fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithoutTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kolmen viikon sisällä.\n\n\nYstävällisin terveisin,\n\nTeam Lauber');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kolmen viikon sisällä. Seurantakoodi on TRACKING_CODE ja pääset katsomaan tilauksesi statusta seuraavasta osoitteesta: http://app3.hongkongpost.com/CGI/mt/enquiry.jsp\n\n\nYstävällisin terveisin,\n\nTeam Lauber');

delete from settings where key = 'fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescription';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescription','Keskitetyn tilaus- ja toimitusprosessimme vuoksi, tilausten käsittely maksun saapumisesta postittamiseen on muutama arkipäivä. Lähetämme tuotteen Hong Kong postin kautta lentorahtina. Hong Post postin lupaama toimitusaika pääkaupunkeihin on 4-6 postin työpäivää ja muualle 7-10 postin työpäivää. Kokonaisuudessaan toimitukseen menee noin kolme viikkoa.');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.EmailFromCustomerProcessor.forwardCustomerEmailsTo','some@email.fi');

alter table ad add column price_euro double precision;
update ad set price_euro = start_price_euro*1.1;
ALTER TABLE ad alter column price_euro set not null;
alter table ad add column sales_type integer;
UPDATE ad set sales_type = 1;
ALTER TABLE ad alter column sales_type set not null;
alter table adtemplate drop column sales_type;
delete from settings where key = 'fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescription';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.HuutonetListerStrategy.shippingDescription','Keskitetyn tilaus- ja toimitusprosessimme vuoksi, tilausten käsittely maksun saapumisesta postittamiseen on noin viikko. Lähetämme tuotteen Hong Kong postin kautta lentorahtina. Hong Post postin lupaama toimitusaika pääkaupunkeihin on 4-6 postin työpäivää ja muualle 7-10 postin työpäivää. Kokemuksen mukaan lähetyksaika muodostuu kuitenkin usein tätä pidemmäksi, jonka vuoksi arviomme kokonaistoimitusajaksi on 2-4 viikkoa maksun saapumisesta.\n\nTuote läheteään huutajan huuto.net-palveluun rekisteröimään osoitteeseen.');
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.DxProcessor.orderShippedEmailWithTrackingCode','Hei,\n\nTilausnumerolla ORDER_ID tilaamanne tuote AD_HEADLINE on tänään lähetetty postin mukana suoraan ilmoittamaanne osoitteeseen:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nTuote saapuu yllä mainittuun osoitteeseen noin kolmen viikon sisällä. Seurantakoodi on TRACKING_CODE ja pääset muutaman päivän sisällä seuraamaan tilauksesi statusta seuraavasta osoitteesta: http://app3.hongkongpost.com/CGI/mt/genresult.jsp?tracknbr=TRACKING_CODE \n\nSuomen postijärjestelmä ei ole vielä integroitu Hong Kong postin kanssa, joten status tietoa tulee tällä hetkellä ainoastaan Hong Kongin päästä.\nYstävällisin terveisin,\n\nTeam Lauber');

insert into settings (key,value) values ('fi.jonix.huutonet.robot.order.DealExtremeProvider.cancelDescription','Too long time to wait for shipping.');

-- database changes for version prod20100601:
update orders set state = 450 where state = 7; -- closed
update orders set state = 340 where state = 101; -- other error
update orders set state = 330 where state = 12; -- money refunded
update orders set state = 290 where state = 6; -- shipped by provider
update orders set state = 280 where state = 9; -- fullfillment center
update orders set state = 260 where state = 11; -- cancel confirmed by provider
update orders set state = 250 where state = 10; -- cancel pending
update orders set state = 240 where state = 13; -- stock shortage
update orders set state = 230 where state = 3; -- order confirmed by provider
update orders set state = 220 where state = 2; -- ordered from provider
update orders set state = 140 where state = 8; -- customer paid informed
update orders set state = 130 where state = 1; -- customer paid
update orders set state = 20 where state = 100; -- paid too little error
update orders set state = 10 where state = 0; -- new

CREATE TABLE state_changes (
	id int8 NOT null,
	state integer not null,
	update_date timestamp without time zone not null,
	order_id int8 NOT null
);
ALTER TABLE ONLY state_changes ADD CONSTRAINT state_changes_pkey PRIMARY KEY (id);
ALTER TABLE state_changes
    ADD CONSTRAINT sc_or_fk
    FOREIGN KEY (order_id)
    REFERENCES orders;
CREATE SEQUENCE state_change_id_seq;

CREATE TABLE state_changes_tmp
(
  id int8 DEFAULT nextval('state_change_id_seq'),
  state integer,
  update_date timestamp without time zone NOT NULL,
  order_id int8 NOT NULL)
WITH (
  OIDS=FALSE
);
ALTER TABLE state_changes_tmp OWNER TO postgres;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_won_bid, id FROM orders;
UPDATE state_changes_tmp SET state = 10; -- new
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT o.date_cust_paid, o.id FROM orders o, ad a, transaction t where t.order_id = o.id and o.ad_id = a.id and
t.amount < (o.fin_sp_euro + a.shipping_cost_euro);
UPDATE state_changes_tmp SET state = 20; -- paid too little
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;
ANVÄND DENNA QUERY ISTÄLLET (MANUELLT KOLLAD)=>SELECT t.amount, o.fin_sp_euro, a.shipping_cost_euro, o.date_cust_paid, o.id FROM orders o, ad a, transaction t where t.order_id = o.id and o.ad_id = a.id and
  t.amount < (o.fin_sp_euro + a.shipping_cost_euro) and o.id not in (7,8,93,103,124,109);

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_cust_paid, id FROM orders where date_cust_paid is not null;
UPDATE state_changes_tmp SET state = 130; -- customer paid
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT (date_cust_paid + interval '5 minutes'), id FROM orders where date_cust_paid is not null;
UPDATE state_changes_tmp SET state = 140; -- customer paid informed
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_ordered_from_prov, id FROM orders where date_ordered_from_prov is not null;
UPDATE state_changes_tmp SET state = 220; -- ordered from provider
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_prov_conf_order, id FROM orders where date_prov_conf_order is not null;
UPDATE state_changes_tmp SET state = 230; -- order confirmed by provider
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

-- stock shortage: denna har ännu inte börjat användas, men kolla för säkerhetsskull med:
select * from orders where state = 240;

-- cancel pending: denna har ännu inte börjat användas, men kolla för säkerhetsskull med:
select * from orders where state = 250;

-- cancel confirmed by provider: denna har ännu inte börjat användas, men kolla för säkerhetsskull med:
select * from orders where state = 260;

-- fullfillment center: denna har ännu inte börjat användas, men kolla för säkerhetsskull med:
select * from orders where state = 280;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_shipped, id FROM orders where date_shipped is not null;
UPDATE state_changes_tmp SET state = 290; -- shipped by provider
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

-- money refunded: denna har ännu inte börjat användas, men kolla för säkerhetsskull med:
select * from orders where state = 330;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_error, id FROM orders where date_error is not null;
UPDATE state_changes_tmp SET state = 340; -- other error
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

INSERT INTO state_changes_tmp (update_date, order_id) SELECT date_closed, id FROM orders where date_closed is not null;
UPDATE state_changes_tmp SET state = 450; -- closed
INSERT INTO state_changes (id, state, update_date, order_id) SELECT id, state, update_date, order_id FROM state_changes_tmp; 
DELETE FROM state_changes_tmp;

DROP TABLE state_changes_tmp;

alter table orders drop column date_won_bid;
alter table orders drop column date_cust_paid;
alter table orders drop column date_ordered_from_prov;
alter table orders drop column date_prov_conf_order;
alter table orders drop column date_m_sent_prov;
alter table orders drop column date_prov_rec_money;
alter table orders drop column date_shipped;
alter table orders drop column date_closed;
alter table orders drop column date_error;

-- changes for version going to production on 2010-06-11
alter table transaction add column oper_descr varchar(255);



insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardFaultyContactInfo','some@email.fi');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.PaypalProcessor.paypalRefundAddress','service@intl.paypal.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.emailOrdersAddress','someemail2@gmail.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.faultyMarketSalesIdEmail','Moi,\n\nHalusit tilata tuotteen tunnisteella MARKET_SALES_ID mutta tämä tunniste\nei löydy meidän tietokannasta. Ole hyvä ja yritä uudelleen.\n\nYstävällisin terveisin,\nTeam Lauber');

-- database updates for version prod20100619 (the version that moves the customer information from the orders table into customer table)
CREATE TABLE customer (
	id int8 NOT null,
	address varchar(255) not null,
	city varchar(255) not null,
	email varchar(255) not null,
	first_name varchar(255) not null,
	last_name varchar(255) not null,
	login_name varchar(255) not null,
	phone_number varchar(255) not null,
	postcode varchar(255) not null
);
ALTER TABLE ONLY customer ADD CONSTRAINT customer_pkey PRIMARY KEY (id);
ALTER TABLE customer ADD CONSTRAINT customer_email_id_key UNIQUE (email);
CREATE SEQUENCE customer_id_seq;
insert into customer (id,address,city,email,first_name,last_name,login_name,phone_number,postcode)
  select nextval('customer_id_seq'), address,city,email,first_name,last_name,login_name,phone_number,postcode
    from (select distinct address,city,email,first_name,last_name,login_name,phone_number,postcode from orders) as foo;
alter table orders add column customer_id int8;
update orders set customer_id = customer.id from customer
  where customer.address = orders.address
  and customer.city = orders.city
  and customer.email = orders.email
  and customer.first_name = orders.first_name
  and customer.last_name = orders.last_name
  and customer.login_name = orders.login_name
  and customer.phone_number = orders.phone_number
  and customer.postcode = orders.postcode;
ALTER TABLE orders
    ADD CONSTRAINT ord_cust_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
alter TABLE orders alter column customer_id set not null;
alter table orders drop address;
alter table orders drop city;
alter table orders drop email;
alter table orders drop first_name;
alter table orders drop last_name;
alter table orders drop login_name;
alter table orders drop phone_number;
alter table orders drop postcode;
-- select o.reference, c.address, c.city, c.email, c.first_name, c.last_name, c.login_name, c.phone_number, c.postcode from orders o, customer c where o.customer_id = c.id and o.id = 60;


-- database updates for the version that makes it possible to create orders that aren't mapped to any ad
insert into market (id,currency,name,url) values (nextval('market_id_seq'),1,'Google-Order','http://laubertea.appspot.com');
alter table orders add column market_id int8;
update orders set market_id = 1;
alter table orders alter column market_id set not null;
ALTER TABLE orders
    ADD CONSTRAINT ord_ma_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
alter table orders add column shipping_cost_euro double precision;
alter table orders add column shipping_cost_mc double precision;
update orders set shipping_cost_euro = ad.shipping_cost_euro, shipping_cost_mc = ad.shipping_cost_mc from ad
  where ad.id = orders.ad_id;
alter table orders alter column shipping_cost_euro set not null;
alter table orders alter column shipping_cost_mc set not null;
alter table orders alter column ad_id drop not null;
alter table orders add column provider_prod_id varchar(255);
update orders set provider_prod_id = product.provider_prod_id from ad, adtemplate, product
  where ad.id = orders.ad_id and ad.ad_template_id = adtemplate.id and adtemplate.product_id = product.id;
alter table orders alter column provider_prod_id set not null;
alter table orders drop constraint orders_prov_order_id_key;
alter table orders drop constraint orders_tracking_code_key;
alter table orders drop constraint orders_transaction_id_key;
alter table orders add column provider_id int8;
update orders set provider_id = 1;
alter table orders alter column provider_id set not null;
ALTER TABLE orders
    ADD CONSTRAINT ord_prov_fk
    FOREIGN KEY (provider_id)
    REFERENCES provider;
CREATE TABLE orders_tmp (
	id int8 NOT null,
	prov_order_id varchar(255),
	transaction_id varchar(255),
 	tracking_code varchar(255)
);
insert into orders_tmp (id,prov_order_id,transaction_id,tracking_code)
  select id, prov_order_id,transaction_id,tracking_code from orders;
update orders set prov_order_id = trim(leading 'DealExtreme_' from orders_tmp.prov_order_id),
  transaction_id = trim(leading 'DealExtreme_' from orders_tmp.transaction_id),
  tracking_code = trim(leading 'DealExtreme_' from orders_tmp.tracking_code)
  from orders_tmp where orders.id = orders_tmp.id;
drop table orders_tmp;
ALTER TABLE orders ADD CONSTRAINT prov_ord_id_key UNIQUE (prov_order_id, provider_id);
ALTER TABLE orders ADD CONSTRAINT tran_ord_id_key UNIQUE (transaction_id, provider_id);
ALTER TABLE orders ADD CONSTRAINT trck_ord_id_key UNIQUE (tracking_code, provider_id);
alter table adtemplate drop shipping_cost_mc;
alter table adtemplate drop shipping_cost_euro;
alter table customer alter column login_name drop not null;

-- database changes for the version that implements the refunding and cancellation stuff
CREATE TABLE refund (
	id int8 NOT null,
	order_id int8 NOT null,
	conf_code varchar(255) not null,
	account_no varchar(255)
);
ALTER TABLE ONLY refund ADD CONSTRAINT refund_pkey PRIMARY KEY (id);
ALTER TABLE refund
    ADD CONSTRAINT ref_or_fk
    FOREIGN KEY (order_id)
    REFERENCES orders;
CREATE SEQUENCE refund_id_seq;

-- database changes for the version that automatically makes refunds to customers
ALTER TABLE refund ADD CONSTRAINT refund_order_key UNIQUE (order_id);
ALTER TABLE refund ADD CONSTRAINT refund_code_key UNIQUE (conf_code);

-- database changes for the version that can handle address changes
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmail';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmail','Kiitos tilauksestanne,\n\nTilausnumeronne on ORDER_ID (tuote AD_HEADLINE)\n\nTilauksenne käsittely jatkuu, kun maksu on rekisteröitynyt tilillemme.\n\nLaskun tiedot:\n\nHandelsbanken\nMaksun saaja: Team Lauber\nTilinro: ACCOUNT_NUMBER\nViitenumero: REFERENCE_NUMBER Viitenumero on pakollinen tieto!\nSumma: TOTAL_PRICE (sisältää tuotteen hinta FINAL_SALES_PRICE ja postikulut SHIPPING_COST)\n\nTuote lähetetään huuto.net:tiin rekisteröityyn osoitteeseenne:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nMikäli haluatte muuttaa osoitetta, johon paketti lähetetään, voitte tehdä sen oheisen linkin kautta: GOOGLE_ORDER_URL/addresschanges?form&marketSalesId=MARKET_SALES_ID&referenceNumber=REFERENCE_NUMBER&orderId=ORDER_ID&firstName=FIRST_NAME_URL_ENC&lastName=LAST_NAME_URL_ENC&address=CUSTOMER_ADDRESS_URL_ENC&postCode=CUSTOMER_POSTCODE_URL_ENC&city=CUSTOMER_CITY_URL_ENC&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nYstävällisin terveisin,\n\nTeam Lauber');

update customer set email = lower(email);

-- database changes for the version that starts using the reference number for google-order's address change instead of marketSalesId
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmail';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmail','Kiitos tilauksestanne,\n\nTilausnumeronne on ORDER_ID AD_HEADLINE\n\nTilauksenne käsittely jatkuu, kun maksu on rekisteröitynyt tilillemme.\n\nLaskun tiedot:\n\nHandelsbanken\nMaksun saaja: Team Lauber\nTilinro: ACCOUNT_NUMBER\nViitenumero: REFERENCE_NUMBER Viitenumero on pakollinen tieto!\nSumma: TOTAL_PRICE (sisältää tuotteen hinta FINAL_SALES_PRICE ja postikulut SHIPPING_COST)\n\nTuote lähetetään osoitteeseenne:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nMikäli haluatte muuttaa osoitetta, johon paketti lähetetään, voitte tehdä sen oheisen linkin kautta: GOOGLE_ORDER_URL/addresschanges?form&referenceNumber=REFERENCE_NUMBER&firstName=FIRST_NAME_URL_ENC&lastName=LAST_NAME_URL_ENC&address=CUSTOMER_ADDRESS_URL_ENC&postCode=CUSTOMER_POSTCODE_URL_ENC&city=CUSTOMER_CITY_URL_ENC&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nYstävällisin terveisin,\n\nTeam Lauber');
alter table customer alter column phone_number drop not null;

alter table adtemplate add column created_timestamp timestamp without time zone;
update adtemplate set created_timestamp = '2010-07-20 00:00:00';
ALTER TABLE adtemplate alter column created_timestamp set not null;

delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.EmailOrderProcessor.emailOrdersAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.EmailOrderProcessor.faultyMarketSalesIdEmail';
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.emailOrdersAddress','someemail2@gmail.com');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.faultyMarketSalesIdEmail','Moi,\n\nHalusit tilata tuotteen tunnisteella MARKET_SALES_ID mutta tämä tunniste\nei löydy meidän tietokannasta. Ole hyvä ja yritä uudelleen.\n\nYstävällisin terveisin,\nTeam Lauber');

insert into settings (key,value) values ('fi.jonix.huutonet.robot.order.CustomerStockShortageInformer.stockShortageApology','Hei,\n\nKävipä nolosti. Varastosaldomme eivät ole olleet ajantasalla ilmoitushetkellä ja tilaamasi tuote AD_HEADLINE tilausnumerolla ORDER_ID on loppunut varastolta. Tilauksen viitenumero on REFERENCE_NUMBER.\n\nVoit peruuttaa tilauksen osoitteessa GOOGLE_ORDER_URL/emailcancels?form&referenceNumber=REFERENCE_NUMBER. Toinen vaihtoehto on jäädä odottamaan täydennystilausta. Vielä ei ole tietoa milloin täydennystilaus tulee, joten ehkä varminta on peruuttaa tilaus.\n\nTilauksen peruutuksen jälkeen lähetämme vielä peruutusvahvistuspyynnön oikeuksiesi turvaamiseksi. Peruutusvahvistuksen jälkeen tilaus on peruutettu ja parin viikon sisällä tilauksesta maksamasi rahat palautetaan peruutusvahvistuksen yhteydessä määrämällesi tilille.\n\nYstävällisin terveisin,\n\nTeam Lauber');

alter table ad add column date_closing timestamp without time zone;
update ad set date_closing = date_from_market;
update ad set date_closing = (date_to_market + interval '5 day') where date_closing is null and ad.ad_template_id in (select id from adtemplate where open_days = 5);
update ad set date_closing = (date_to_market + interval '3 day') where date_closing is null and ad.ad_template_id in (select id from adtemplate where open_days = 3);
ALTER TABLE ad alter column date_closing set not null;

CREATE TABLE category (
	id int8 NOT null,
	name varchar(255) not null,
	parent_category_id int8,
	market_spec_id integer not null,
	market_id int8 not null
);
ALTER TABLE ONLY category ADD CONSTRAINT category_pkey PRIMARY KEY (id);
CREATE SEQUENCE category_id_seq;
ALTER TABLE category
    ADD CONSTRAINT cat_cat_fk
    FOREIGN KEY (parent_category_id)
    REFERENCES category;
ALTER TABLE category
    ADD CONSTRAINT cat_mark_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE category ADD CONSTRAINT name_par_id_key UNIQUE (name, parent_category_id);
ALTER TABLE category ADD CONSTRAINT mar_marsp_id_key UNIQUE (market_id, market_spec_id);

CREATE TABLE category_mapping (
	id int8 NOT null,
	category_id1 int8 not null,
	category_id2 int8 not null
);
ALTER TABLE ONLY category_mapping ADD CONSTRAINT category_map_pkey PRIMARY KEY (id);
CREATE SEQUENCE category_map_id_seq;
ALTER TABLE category_mapping
    ADD CONSTRAINT map_cato_fk
    FOREIGN KEY (category_id1)
    REFERENCES category;
ALTER TABLE category_mapping
    ADD CONSTRAINT map_catt_fk
    FOREIGN KEY (category_id2)
    REFERENCES category;
ALTER TABLE category_mapping ADD CONSTRAINT cato_catt_id_key UNIQUE (category_id1, category_id2);

insert into market (id,currency,name,url) values (nextval('market_id_seq'),1,'SellStar','http://localhost');
insert into category (id,name,market_id,market_spec_id) values (nextval('category_id_seq'),'SellStar',3,999999);
update category set market_spec_id = currval('category_id_seq') where id = currval('category_id_seq');
insert into category (id,name,market_id,market_spec_id) values (nextval('category_id_seq'),'Huutonet',1,999999);
update category set market_spec_id = currval('category_id_seq') where id = currval('category_id_seq');

alter table product add column category_id int8;
ALTER TABLE product
    ADD CONSTRAINT prod_cat_fk
    FOREIGN KEY (category_id)
    REFERENCES category;

alter table adtemplate alter column category drop not null;
alter table adtemplate alter column sub_category drop not null;

alter table ad add column market_category_id int8;
update ad set market_category_id = category.id from category, adtemplate where ad.ad_template_id = adtemplate.id and market_spec_id = to_number(adtemplate.sub_category,'9999999');
ALTER TABLE ad
    ADD CONSTRAINT ad_cat_fk
    FOREIGN KEY (market_category_id)
    REFERENCES category;
alter table ad alter column market_category_id set not null;

alter table orders add column send_count integer;
update orders set send_count = 0;
update orders set send_count = 1 where state >= 220 and state < 460;
update orders set send_count = 1 where state = 470 and id <> 253;
alter table orders alter column send_count set not null;
update settings set key = 'fi.jonix.huutonet.robot.order.CustomerStockShortageInformer.stockShortageApology' where key = 'fi.jonix.huutonet.robot.order.DealExtremeProvider.stockShortageApology';

alter table adtemplate drop COLUMN category;
alter table adtemplate drop COLUMN sub_category ;


alter table orders add column other_loss_euro numeric(12,6);
update orders set other_loss_euro = 0;
alter table orders alter column other_loss_euro set not null;

alter table orders add column targeted_mc numeric(12,6);
update orders o set targeted_mc = (select sum(t.amount) from transaction t where t.amount > 0 and t.order_id = o.id);
update orders set targeted_mc = 0 where targeted_mc is null;
alter table orders alter column targeted_mc set not null;

alter table orders add column fin_sp_mc_tmp numeric(12,6);
update orders set fin_sp_mc_tmp = fin_sp_mc;
alter table orders alter column fin_sp_mc_tmp set not null;
alter table orders drop COLUMN fin_sp_mc;
alter table orders RENAME column fin_sp_mc_tmp to fin_sp_mc;

alter table orders add column fin_sp_euro_tmp numeric(12,6);
update orders set fin_sp_euro_tmp = fin_sp_euro;
alter table orders alter column fin_sp_euro_tmp set not null;
alter table orders drop COLUMN fin_sp_euro;
alter table orders RENAME column fin_sp_euro_tmp to fin_sp_euro;

alter table orders add column fin_pp_pc_tmp numeric(12,6);
update orders set fin_pp_pc_tmp = fin_pp_pc;
alter table orders drop COLUMN fin_pp_pc;
alter table orders RENAME column fin_pp_pc_tmp to fin_pp_pc;

alter table orders add column fin_pp_euro_tmp numeric(12,6);
update orders set fin_pp_euro_tmp = fin_pp_euro;
alter table orders drop COLUMN fin_pp_euro;
alter table orders RENAME column fin_pp_euro_tmp to fin_pp_euro;

alter table orders add column shipping_cost_mc_tmp numeric(12,6);
update orders set shipping_cost_mc_tmp = shipping_cost_mc;
alter table orders alter column shipping_cost_mc_tmp set not null;
alter table orders drop COLUMN shipping_cost_mc;
alter table orders RENAME column shipping_cost_mc_tmp to shipping_cost_mc;

alter table orders add column shipping_cost_euro_tmp numeric(12,6);
update orders set shipping_cost_euro_tmp = shipping_cost_euro;
alter table orders alter column shipping_cost_euro_tmp set not null;
alter table orders drop COLUMN shipping_cost_euro;
alter table orders RENAME column shipping_cost_euro_tmp to shipping_cost_euro;

alter table transaction add column amount_tmp numeric(12,6);
update transaction set amount_tmp = amount;
alter table transaction alter column amount_tmp set not null;
alter table transaction drop COLUMN amount;
alter table transaction RENAME column amount_tmp to amount;

alter table ad add column start_price_mc_tmp numeric(12,6);
update ad set start_price_mc_tmp = start_price_mc;
alter table ad alter column start_price_mc_tmp set not null;
alter table ad drop COLUMN start_price_mc;
alter table ad RENAME column start_price_mc_tmp to start_price_mc;

alter table ad add column start_price_euro_tmp numeric(12,6);
update ad set start_price_euro_tmp = start_price_euro;
alter table ad alter column start_price_euro_tmp set not null;
alter table ad drop COLUMN start_price_euro;
alter table ad RENAME column start_price_euro_tmp to start_price_euro;

alter table ad add column price_euro_tmp numeric(12,6);
update ad set price_euro_tmp = price_euro;
alter table ad alter column price_euro_tmp set not null;
alter table ad drop COLUMN price_euro;
alter table ad RENAME column price_euro_tmp to price_euro;

alter table ad add column min_selling_price_mc_tmp numeric(12,6);
update ad set min_selling_price_mc_tmp = min_selling_price_mc;
alter table ad alter column min_selling_price_mc_tmp set not null;
alter table ad drop COLUMN min_selling_price_mc;
alter table ad RENAME column min_selling_price_mc_tmp to min_selling_price_mc;

alter table ad add column min_selling_price_euro_tmp numeric(12,6);
update ad set min_selling_price_euro_tmp = min_selling_price_euro;
alter table ad alter column min_selling_price_euro_tmp set not null;
alter table ad drop COLUMN min_selling_price_euro;
alter table ad RENAME column min_selling_price_euro_tmp to min_selling_price_euro;

alter table ad add column prov_price_at_listing_pc_tmp numeric(12,6);
update ad set prov_price_at_listing_pc_tmp = prov_price_at_listing_pc;
alter table ad alter column prov_price_at_listing_pc_tmp set not null;
alter table ad drop COLUMN prov_price_at_listing_pc;
alter table ad RENAME column prov_price_at_listing_pc_tmp to prov_price_at_listing_pc;

alter table ad add column prov_price_at_listing_euro_tmp numeric(12,6);
update ad set prov_price_at_listing_euro_tmp = prov_price_at_listing_euro;
alter table ad alter column prov_price_at_listing_euro_tmp set not null;
alter table ad drop COLUMN prov_price_at_listing_euro;
alter table ad RENAME column prov_price_at_listing_euro_tmp to prov_price_at_listing_euro;

alter table ad add column shipping_cost_mc_tmp numeric(12,6);
update ad set shipping_cost_mc_tmp = shipping_cost_mc;
alter table ad alter column shipping_cost_mc_tmp set not null;
alter table ad drop COLUMN shipping_cost_mc;
alter table ad RENAME column shipping_cost_mc_tmp to shipping_cost_mc;

alter table ad add column shipping_cost_euro_tmp numeric(12,6);
update ad set shipping_cost_euro_tmp = shipping_cost_euro;
alter table ad alter column shipping_cost_euro_tmp set not null;
alter table ad drop COLUMN shipping_cost_euro;
alter table ad RENAME column shipping_cost_euro_tmp to shipping_cost_euro;

alter table adtemplate add column min_bid_increase_mc_tmp numeric(12,6);
update adtemplate set min_bid_increase_mc_tmp = min_bid_increase_mc;
alter table adtemplate alter column min_bid_increase_mc_tmp set not null;
alter table adtemplate drop COLUMN min_bid_increase_mc;
alter table adtemplate RENAME column min_bid_increase_mc_tmp to min_bid_increase_mc;

alter table adtemplate add column min_bid_increase_euro_tmp numeric(12,6);
update adtemplate set min_bid_increase_euro_tmp = min_bid_increase_euro;
alter table adtemplate alter column min_bid_increase_euro_tmp set not null;
alter table adtemplate drop COLUMN min_bid_increase_euro;
alter table adtemplate RENAME column min_bid_increase_euro_tmp to min_bid_increase_euro;


alter table orders drop COLUMN paid_too_much;
update settings set value = 'Hei,\n\nKävipä nolosti. Varastosaldomme eivät ole olleet ajantasalla ilmoitushetkellä ja tilaamasi tuote AD_HEADLINE tilausnumerolla ORDER_ID on loppunut varastolta. Tilauksen viitenumero on REFERENCE_NUMBER.\n\nVoit peruuttaa tilauksen osoitteessa GOOGLE_ORDER_URL/emailcancels?form&referenceNumber=REFERENCE_NUMBER. Toinen vaihtoehto on jäädä odottamaan täydennystilausta. Vielä ei ole tietoa milloin täydennystilaus tulee, joten ehkä varminta on peruuttaa tilaus.\n\nTilauksen peruutuksen jälkeen lähetämme vielä peruutusvahvistuspyynnön oikeuksiesi turvaamiseksi. Peruutusvahvistuksen jälkeen tilaus on peruutettu ja parin viikon sisällä tilauksesta maksamasi rahat palautetaan peruutusvahvistuksen yhteydessä määrämällesi tilille.\n\nTäydennyseriä saapuu jatkuvasti ja niitä odotelleet tilaukset käsitellään pikaisesti. Tästä johtuen tilaus on voitu ehtiä paketoida ja antaa postin kuljetettavaksi ennen kuin peruutusvahvistus saavuttaa varaston. Tällöin peruutusta ei luonnollisesti voida enää toimeenpanna. Mikäli saat peruutusvahvistuksen jälkeen viikon sisällä viestin, että tilaus on lähetetty, niin voitte jäädä odottelemaan tilaamanne tuotteen saapumista.\n\nYstävällisin terveisin,\n\nTeam Lauber' where key = 'fi.jonix.huutonet.robot.order.CustomerStockShortageInformer.stockShortageApology';


update settings set value = 'Kiitos tilauksestanne,\n\nTilausnumeronne on ORDER_ID AD_HEADLINE\n\nTilauksenne käsittely jatkuu, kun maksu on rekisteröitynyt tilillemme.\n\nLaskun tiedot:\n\nHandelsbanken\nMaksun saaja: Team Lauber\nTilinro: ACCOUNT_NUMBER\nViitenumero: REFERENCE_NUMBER  (HUOM! Viitenumero on pakollinen tieto)\nSumma: TOTAL_PRICE (sisältää tuotteen hinnan FINAL_SALES_PRICE ja postikulut SHIPPING_COST)\n\nMuistathan käyttää viitenumeroa, koska tilausjärjestelmämme tarkistaa laskut automaattisesti viitenumeron perusteella. Tilausjärjestelmä ilmoittaa sähköpostitse, kun rahat ovat kirjautuneet tilillemme.\n\nMikäli olet tilannut kerralla useamman tuotteen ja haluat maksaa kaiken samalla laskulla, niin se onnistuu seuraavan linkin takaa aukeavalla koontilaskun pyyntölomakkeella:  GOOGLE_ORDER_URL/bills?form&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nTuote lähetetään osoitteeseenne:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nMikäli haluatte muuttaa osoitetta, johon paketti lähetetään, voitte tehdä sen oheisen linkin kautta: GOOGLE_ORDER_URL/addresschanges?form&referenceNumber=REFERENCE_NUMBER&firstName=FIRST_NAME_URL_ENC&lastName=LAST_NAME_URL_ENC&address=CUSTOMER_ADDRESS_URL_ENC&postCode=CUSTOMER_POSTCODE_URL_ENC&city=CUSTOMER_CITY_URL_ENC&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nYstävällisin terveisin,\n\nTeam Lauber' where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmail';


insert into market (id,currency,name,url) values (nextval('market_id_seq'),1,'Mikko','http://www.mikko.fi');
insert into category (id,name,market_id,market_spec_id) values (nextval('category_id_seq'),'Mikko',(select id from market where name = 'Mikko'),999999);
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.mikkofi.MikkofiLister.userName','SomeUserName');
insert into settings (key,value) values ('fi.jonix.huutonet.robot.lister.mikkofi.MikkofiLister.password','somepassword');
alter table category drop constraint mar_marsp_id_key;
insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.MikkoProcessor.mikkoAddress','mikko-asiakaspalvelu@mikko.fi');


CREATE TABLE product_keywords (
	id int8 NOT null,
	keyword varchar(255) not null,
	product_id int8 not null
);
CREATE SEQUENCE product_keyword_id_seq;
ALTER TABLE ONLY product_keywords ADD CONSTRAINT product_keyword_pkey PRIMARY KEY (id);
ALTER TABLE product_keywords
    ADD CONSTRAINT pk_prod_fk
    FOREIGN KEY (product_id)
    REFERENCES product;


alter table adtemplate drop column market_id;


alter table product add column dimensions text;
alter table product add column contents text;


CREATE TABLE login_name (
	id int8 NOT null,
	customer_id int8 not null,
	market_id int8 not null,
	login_name varchar(255) not null
);
ALTER TABLE ONLY login_name ADD CONSTRAINT login_name_pkey PRIMARY KEY (id);
ALTER TABLE login_name ADD CONSTRAINT lg_lgm_key UNIQUE (customer_id, market_id);
ALTER TABLE login_name
    ADD CONSTRAINT lg_c_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
ALTER TABLE login_name
    ADD CONSTRAINT lg_m_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
CREATE SEQUENCE login_name_id_seq;
INSERT INTO login_name (id,customer_id,market_id,login_name)
  select nextval('login_name_id_seq'), id, market_id, login_name
  	from (select distinct c.id, o.market_id, c.login_name from orders o, customer c
  	  where o.customer_id = c.id and o.market_id in (1,4) and login_name is not null) as foo;
alter table customer drop column login_name;


insert into settings (key,value) values ('productNoLongerForSaleAtProvider','Hei,\n\nKävipä nolosti. Ehdimme myydä huutoaikana varaston loppuun ko. tuotteen osalta ja tilaamaasi tuotetta AD_HEADLINE tilausnumerolla ORDER_ID ei ole toistaiseksi saatavilla. Tilauksen viitenumero on REFERENCE_NUMBER.\n\nPeruuta tilauksesi osoitteessa GOOGLE_ORDER_URL/emailcancels?form&referenceNumber=REFERENCE_NUMBER.\n\nTilauksen peruutuksen jälkeen lähetämme vielä peruutusvahvistuspyynnön oikeuksiesi turvaamiseksi. Peruutusvahvistuksen jälkeen tilaus on peruutettu ja parin viikon sisällä tilauksesta maksamasi rahat palautetaan peruutusvahvistuksen yhteydessä määrämällesi tilille.\n\nPahoittelut aiheuttamastamme vaivannäöstäsi. Pyrimme pitämään toiminnan kulut mahdollisimman matalina ja siirtämään aiheutunut hyöty suoraan hintoihin. Asiakkaamme nauttivat poikkeuksellisen edullisista hinnoista, mutta myös satunnaisia tuote-erien loppumisia voi tapahtua.\n\nYstävällisin terveisin,\n\nTeam Lauber');


insert into settings (key,value) values ('fi.jonix.huutonet.order.focalprice.FocalPriceProvider.maskFile','TeamLauber_FocalPrice_mask.jpg');


alter table provider add column username varchar(32);
alter table provider add column password varchar(32);
update provider set username = 'someemail@gmail.com', password = 'somepass' where id = 1;
update provider set username = 'someemail@gmail.com', password = 'somepass' where id = 2;
alter table market add column username varchar(32);
alter table market add column password varchar(32);
update market set username = 'SomeUserName', password = 'somepass' where name = 'Huutonet';
update market set username = '', password = '' where name = 'HuutonetSNAPSHOT';
update market set username = 'SomeUserName', password = 'somepass' where name = 'Mikko';
update market set username = '', password = '' where name = 'MikkoSNAPSHOT';
update market set username = '', password = '' where name = 'Google-Order';
update market set username = '', password = '' where name = 'SellStar';

delete from settings where key = 'fi.jonix.huutonet.robot.lister.HuutonetLister.userName';
delete from settings where key = 'fi.jonix.huutonet.robot.lister.HuutonetLister.password';
delete from settings where key = 'fi.jonix.huutonet.robot.lister.mikkofi.MikkofiLister.userName';
delete from settings where key = 'fi.jonix.huutonet.robot.lister.mikkofi.MikkofiLister.password';
delete from settings where key = 'mail.inbound.host';
delete from settings where key = 'mail.inbound.username';
delete from settings where key = 'mail.inbound.store.protocol';
delete from settings where key = 'mail.inbound.port';
delete from settings where key = 'mail.inbound.sslInUse';
delete from settings where key = 'mail.outbound.protocol';
delete from settings where key = 'mail.outbound.host';
delete from settings where key = 'mail.outbound.username';
delete from settings where key = 'mail.inbound.password';
delete from settings where key = 'mail.outbound.password';
delete from settings where key = 'mail.outbound.port';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardPublicQuestionsTo';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardPrivateQuestionsTo';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardUnknownEmailsTo';
delete from settings where key = 'bugReportEmailAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.EmailFromCustomerProcessor.forwardCustomerEmailsTo';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardErrorsTo';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.ForwardProcessor.forwardOperationProblemsTo';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.forwardFaultyContactInfo';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.PaypalProcessor.paypalAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.DxProcessor.dxAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.huutonetAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.huutonetAddressWebMaster';
delete from settings where key = 'fi.jonix.huutonet.order.focalprice.FocalPriceProcessor.paypalAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.ForwardProcessor.operators';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.MikkoProcessor.mikkoAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.PaypalProcessor.paypalRefundAddress';
delete from settings where key = 'fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.emailOrdersAddress';

alter table adtemplate alter column technical_specs drop not null;


insert into settings (key,value) values ('fi.jonix.huutonet.order.focalprice.FocalPriceProcessor.paypalAddress','service@intl.paypal.com');


insert into settings (key,value) values ('bugReportEmailAddress','some@email.fi');


CREATE TABLE users (
	id int8 NOT null,
	name varchar(64) not null,
	login_name varchar(16) not null,
	password_md5 varchar(64) not null,
	operator boolean not null,
	email_address varchar(64) not null,
	fwd_bugs boolean not null,
	fwd_glob_err boolean not null,
	fwd_glob_oper_pr boolean not null,
	fwd_prov_unkn_em boolean not null
);
CREATE SEQUENCE users_id_seq;
ALTER TABLE ONLY users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE users ADD CONSTRAINT users_login_name_key UNIQUE (login_name);
insert into users (id,name,login_name,password_md5,operator,email_address,fwd_bugs,fwd_glob_err,fwd_glob_oper_pr,fwd_oper_unkn_em) values (nextval('users_id_seq'),'User1 Full Name','user1','pwd1md5',true,'some@email.fi',true,true,true,true);
insert into users (id,name,login_name,password_md5,operator,email_address,fwd_bugs,fwd_glob_err,fwd_glob_oper_pr,fwd_oper_unkn_em) values (nextval('users_id_seq'),'User2 Full Name','user2','pwd2md5',true,'someemail2@gmail.com',false,false,false,true);
insert into users (id,name,login_name,password_md5,operator,email_address,fwd_bugs,fwd_glob_err,fwd_glob_oper_pr,fwd_oper_unkn_em) values (nextval('users_id_seq'),'User3 Full Name','user3','pwd3md5',false,'someemail3@kolumbus.fi',false,false,false,false);
insert into users (id,name,login_name,password_md5,operator,email_address,fwd_bugs,fwd_glob_err,fwd_glob_oper_pr,fwd_oper_unkn_em) values (nextval('users_id_seq'),'User4 Full Name','user4','pwd4md5',false,'someemail4@hotmail.com',false,false,false,false);

CREATE TABLE seller (
	id int8 NOT null,
	name varchar(64) not null,
	signature varchar(64) not null,
	email_address varchar(64) not null,
	email_password varchar(64) not null,
	email_inbound_protocol varchar(64) not null,
	email_outbound_protocol varchar(64) not null,
	email_inbound_port integer not null,
	email_outbound_port integer not null,
	email_inbound_ssl boolean not null,
	email_inbound_host varchar(64) not null,
	email_outbound_host varchar(64) not null,
	user_id int8 not null,
	fwd_public_quest varchar(128),
	fwd_private_quest varchar(128),
	fwd_unknown_emails varchar(128),
	fwd_customer_emails varchar(128),
	fwd_error_reports varchar(128),
	fwd_oper_problems varchar(128),
	error boolean not null,
	margin numeric(12,6) not null,
	active boolean not null
);
CREATE SEQUENCE seller_id_seq;
ALTER TABLE ONLY seller ADD CONSTRAINT seller_pkey PRIMARY KEY (id);
ALTER TABLE seller ADD CONSTRAINT seller_signature_key UNIQUE (signature);
ALTER TABLE seller ADD CONSTRAINT seller_email_address_key UNIQUE (email_address);
ALTER TABLE seller
    ADD CONSTRAINT sell_use_fk
    FOREIGN KEY (user_id)
    REFERENCES users;
insert into seller (id,name,signature,email_address,email_password,email_inbound_protocol,email_outbound_protocol,email_inbound_port,email_outbound_port,email_inbound_ssl,email_inbound_host,email_outbound_host,user_id,fwd_public_quest,fwd_private_quest,fwd_unknown_emails,fwd_customer_emails,fwd_error_reports,fwd_oper_problems,error,margin,active) values (nextval('seller_id_seq'),'Team Lauber','Team Lauber','someemail@gmail.com','somepass','imap','smtp',993,465,true,'imap.gmail.com','smtp.gmail.com',2,null,null,'some@email.fi,someemail2@gmail.com','some@email.fi','some@email.fi','some@email.fi',false,1.43,true);

CREATE TABLE seller_market (
	id int8 NOT null,
	seller_id int8 not null,
	market_id int8 not null,
	user_name varchar(64) not null,
	password varchar(64) not null,
	lister_bean varchar(64) not null
);
CREATE SEQUENCE seller_market_id_seq;
ALTER TABLE ONLY seller_market ADD CONSTRAINT seller_market_pkey PRIMARY KEY (id);
ALTER TABLE seller_market
    ADD CONSTRAINT sm_sel_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
ALTER TABLE seller_market
    ADD CONSTRAINT sm_mar_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE seller_market ADD CONSTRAINT sm_sm_id_key UNIQUE (seller_id, market_id);
ALTER TABLE seller_market ADD CONSTRAINT mu_sm_id_key UNIQUE (market_id, user_name);
insert into seller_market (id,seller_id,market_id,user_name,password,lister_bean) values (nextval('seller_market_id_seq'),1,1,'SomeUserName','somepass','huutonetAdDecorator');
insert into seller_market (id,seller_id,market_id,user_name,password,lister_bean) values (nextval('seller_market_id_seq'),1,4,'SomeUserName','somepass','mikkoAdDecorator');

alter table market drop column username;
alter table market drop column password;
alter table market add column error boolean;
update market set error = false;

alter table market alter column error set not null;

alter table provider add column email_address varchar(64);
alter table provider add column email_password varchar(64);
alter table provider add column email_inbound_protocol varchar(64);
alter table provider add column email_outbound_protocol varchar(64);
alter table provider add column email_inbound_port integer;
alter table provider add column email_outbound_port integer;
alter table provider add column email_inbound_ssl boolean;
alter table provider add column email_inbound_host varchar(64);
alter table provider add column email_outbound_host varchar(64);
alter table provider add column error boolean;
update provider set email_address = 'someemail@gmail.com', email_password = 'somepass', email_inbound_protocol = 'imap', email_outbound_protocol = 'smtp', email_inbound_port = 993, email_outbound_port = 465, email_inbound_ssl = true, email_inbound_host = 'imap.gmail.com', email_outbound_host = 'smtp.gmail.com', error = false;

alter table provider alter column error set not null;

alter table adtemplate add column seller_id int8;
alter table orders add column seller_id int8;

update adtemplate set seller_id = 1;
alter table adtemplate alter column seller_id set not null;
update orders set seller_id = 1;
alter table orders alter column seller_id set not null;

alter table adtemplate add column language varchar(16);
alter table market add column language varchar(16);
update adtemplate set language = 'fi_FI';
update market set language = 'fi_FI';

update adtemplate set language = 'fi_FI';
alter table adtemplate alter column language set not null;
update market set language = 'fi_FI';
alter table market alter column language set not null;

CREATE TABLE message (
	id int8 NOT null,
	key varchar(32) not null,
	seller_id int8,
	language varchar(16) not null,
	text varchar(2048) not null
);
CREATE SEQUENCE message_id_seq;
ALTER TABLE ONLY message ADD CONSTRAINT message_pkey PRIMARY KEY (id);
ALTER TABLE message ADD CONSTRAINT mes_ksl_key UNIQUE (key, seller_id, language);
ALTER TABLE message
    ADD CONSTRAINT mes_sel_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;

alter table translation add column seller_id int8;
update translation set seller_id = 1;
ALTER TABLE translation
    ADD CONSTRAINT tra_sel_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;

update translation set seller_id = 1;
alter table translation alter column seller_id set not null;

ALTER TABLE users RENAME COLUMN fwd_prov_unkn_em TO fwd_oper_unkn_em;

CREATE TABLE system_email (
	id int8 NOT null,
	email_address varchar(64) not null,
	email_password varchar(64) not null,
	email_inbound_protocol varchar(64) not null,
	email_outbound_protocol varchar(64) not null,
	email_inbound_port integer not null,
	email_outbound_port integer not null,
	email_inbound_ssl boolean not null,
	email_inbound_host varchar(64) not null,
	email_outbound_host varchar(64) not null
);
CREATE SEQUENCE system_email_id_seq;
ALTER TABLE ONLY system_email ADD CONSTRAINT system_email_pkey PRIMARY KEY (id);
ALTER TABLE system_email ADD CONSTRAINT system_email_address_key UNIQUE (email_address);

ALTER TABLE seller DROP CONSTRAINT seller_email_address_key;
ALTER TABLE seller DROP COLUMN email_address;
ALTER TABLE seller DROP COLUMN email_password;
ALTER TABLE seller DROP COLUMN email_inbound_protocol;
ALTER TABLE seller DROP COLUMN email_outbound_protocol;
ALTER TABLE seller DROP COLUMN email_inbound_port;
ALTER TABLE seller DROP COLUMN email_outbound_port;
ALTER TABLE seller DROP COLUMN email_inbound_ssl;
ALTER TABLE seller DROP COLUMN email_inbound_host;
ALTER TABLE seller DROP COLUMN email_outbound_host;
ALTER TABLE seller ADD COLUMN system_email_id int8;
update seller set system_email_id = 1;
alter table seller alter column system_email_id set not null;
ALTER TABLE seller
    ADD CONSTRAINT sell_se_fk
    FOREIGN KEY (system_email_id)
    REFERENCES system_email;
ALTER TABLE provider DROP COLUMN email_address;
ALTER TABLE provider DROP COLUMN email_password;
ALTER TABLE provider DROP COLUMN email_inbound_protocol;
ALTER TABLE provider DROP COLUMN email_outbound_protocol;
ALTER TABLE provider DROP COLUMN email_inbound_port;
ALTER TABLE provider DROP COLUMN email_outbound_port;
ALTER TABLE provider DROP COLUMN email_inbound_ssl;
ALTER TABLE provider DROP COLUMN email_inbound_host;
ALTER TABLE provider DROP COLUMN email_outbound_host;
ALTER TABLE provider ADD COLUMN system_email_id int8;
update provider set system_email_id = 1;
ALTER TABLE provider
    ADD CONSTRAINT prov_se_fk
    FOREIGN KEY (system_email_id)
    REFERENCES system_email;
update email set direction = 3 where direction = 2;
ALTER TABLE email ADD COLUMN system_email_id int8;
update email set system_email_id = 1;
ALTER TABLE email
    ADD CONSTRAINT emai_se_fk
    FOREIGN KEY (system_email_id)
    REFERENCES system_email;
ALTER TABLE email ADD COLUMN sending_attempts integer;
update email set sending_attempts = 0;
update email set sending_attempts = 1 where direction = 3 and type = 1;
CREATE INDEX email_typ_index ON email (type);
CREATE INDEX email_dir_index ON email (direction);

update email set system_email_id = 1;
alter table email alter column system_email_id set not null;
alter table email alter column sending_attempts set not null;
alter table provider alter column system_email_id set not null;
alter table provider alter column username set not null;
alter table provider alter column password set not null;


delete from settings where key = 'fi.jonix.huutonet.robot.lister.BaseAdCreator.priceMultiplier';


ALTER TABLE users ADD CONSTRAINT users_address_key UNIQUE (email_address);


insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.GoogleOrderProcessor.noLongerForSale','Moi,\n\nHalusit tilata tuotteen tunnisteella MARKET_SALES_ID mutta tämä tuote ei ole enää tuotevalikoimassamme.\n\nYstävällisin terveisin,\nTeam Lauber');
ALTER TABLE seller ADD COLUMN store_url varchar(128);
update seller set store_url = 'http://laubertea.appspot.com' where id = 1;
update seller set store_url = 'http://somestore.appspot.com' where id = 2;
alter table seller alter column store_url set not null;


insert into settings (key,value) values ('fi.jonix.huutonet.robot.email.processor.HuutonetProcessor.bidWonEmailOutOfStock','Moi,\n\nTilausnumeronne on ORDER_ID AD_HEADLINE\n\nKävipä nolosti. Ilmoitushetkellä korvamerkitty tuotteemme onkin jo ehtinyt toisen asiakkaan haltuun. Käytännössä halutetessasi ilmoituksen AD_HEADLINE tilausnumerolla ORDER_ID huutamasi tuote käsitellään seuraavasta täydennyserästä. Tilauksen viitenumero on REFERENCE_NUMBER.\n\nTäydennyseriä saapuu jatkuvasti ja niitä odotelleet tilaukset käsitellään pikaisesti. Emme kuitenkaan voi taata, että tilauksesi saadaan matkaan edes parin viikon sisällä johtuen tavaran toimittajien toimitustilanteesta. Joissakin tilanteissa täydennyserien saaminen voi viedä tätäkin kauemmin ja mikäli toivoit saavasi tuotteen pikaisesti, niin ehkä kannattaa suoraan peruuttaa tilaus osoitteessa  GOOGLE_ORDER_URL/emailcancels?form&referenceNumber=REFERENCE_NUMBER.\n\nVoit myös halutessasi maksaa tilauksesi alla olevin tiedoin ja jäädä odottelemaan tilauksen saapumista. Kun uusi tavaraerä tilaamaasi tuotetta saapuu, niin olet luonnollisesti etusijalla tilausten käsittelyssä.\n\nLaskun tiedot (tilauksen käsittely jatkuu, kun maksu on rekisteröitynyt tilillemme):\n\nHandelsbanken\nMaksun saaja: Team Lauber\nTilinro: ACCOUNT_NUMBER\nViitenumero: REFERENCE_NUMBER  (HUOM! Viitenumero on pakollinen tieto)\nSumma: TOTAL_PRICE (sisältää tuotteen hinnan FINAL_SALES_PRICE ja postikulut SHIPPING_COST)\n\nMuistathan käyttää viitenumeroa, koska tilausjärjestelmämme tarkistaa laskut automaattisesti viitenumeron perusteella. Tilausjärjestelmä ilmoittaa sähköpostitse, kun rahat ovat kirjautuneet tilillemme.\n\nMikäli olet tilannut kerralla useamman tuotteen ja haluat maksaa kaiken samalla laskulla, niin se onnistuu seuraavan linkin takaa aukeavalla koontilaskun pyyntölomakkeella:  GOOGLE_ORDER_URL/bills?form&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nTuote lähetetään osoitteeseenne:\n\nCUSTOMER_NAME\nCUSTOMER_ADDRESS\nCUSTOMER_POSTCODE\nCUSTOMER_CITY\n\nMikäli haluatte muuttaa osoitetta, johon paketti lähetetään, voitte tehdä sen oheisen linkin kautta: GOOGLE_ORDER_URL/addresschanges?form&referenceNumber=REFERENCE_NUMBER&firstName=FIRST_NAME_URL_ENC&lastName=LAST_NAME_URL_ENC&address=CUSTOMER_ADDRESS_URL_ENC&postCode=CUSTOMER_POSTCODE_URL_ENC&city=CUSTOMER_CITY_URL_ENC&emailAddress=CUSTOMER_EMAIL_URL_ENC\n\nYstävällisin terveisin,\n\nTeam Lauber');


ALTER TABLE product ADD COLUMN listing_failed timestamp without time zone;


ALTER TABLE system_email ADD COLUMN name varchar(64);
update system_email set name = 'Team Lauber' where email_address = 'someemail@gmail.com';
alter table system_email alter column name set not null;


ALTER TABLE adtemplate
    ADD CONSTRAINT adt_sell_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
ALTER TABLE orders
    ADD CONSTRAINT ord_sell_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;


ALTER TABLE seller_market ADD COLUMN ship_cost_mark_curr numeric(12,6);
update seller_market set ship_cost_mark_curr = 1;
alter table seller_market alter column ship_cost_mark_curr set not null;
ALTER TABLE seller ADD COLUMN tracking_always boolean;
update seller set tracking_always = false;
alter table seller alter column tracking_always set not null;

ALTER TABLE orders ADD COLUMN last_cust_update_sent timestamp without time zone;
update orders set last_cust_update_sent = '2011-06-01 00:00:00';
alter table orders alter column last_cust_update_sent set not null;

ALTER TABLE seller ADD COLUMN delivery_text varchar(4096);
alter table seller alter column delivery_text set not null;

ALTER TABLE orders ADD COLUMN first_name varchar(255);
ALTER TABLE orders ADD COLUMN last_name varchar(255);
ALTER TABLE orders ADD COLUMN address varchar(255);
ALTER TABLE orders ADD COLUMN postcode varchar(255);
ALTER TABLE orders ADD COLUMN city varchar(255);
alter table orders alter column first_name set not null;
alter table orders alter column last_name set not null;
alter table orders alter column address set not null;
alter table orders alter column postcode set not null;
alter table orders alter column city set not null;
ALTER TABLE customer DROP COLUMN first_name;
ALTER TABLE customer DROP COLUMN last_name;
ALTER TABLE customer DROP COLUMN address;
ALTER TABLE customer DROP COLUMN postcode;
ALTER TABLE customer DROP COLUMN city;


ALTER TABLE provider ADD COLUMN bundle_support boolean;
alter table provider alter column bundle_support set not null;
ALTER TABLE orders ADD COLUMN try_to_bundle boolean;
alter table orders alter column try_to_bundle set not null;


CREATE TABLE order_bundle (
	id int8 NOT null,
	prov_order_id varchar(255),
	transaction_id varchar(255),
 	tracking_code varchar(255),
	customer_id int8 not null,
	provider_id int8 not null,
	seller_id int8 not null,
	last_cust_update_sent timestamp without time zone not null,
	first_name varchar(255) not null,
	last_name varchar(255) not null,
	address varchar(255) not null,
	postcode varchar(255) not null,
	city varchar(255) not null
);
ALTER TABLE ONLY order_bundle ADD CONSTRAINT order_bundle_pkey PRIMARY KEY (id);
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_cust_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
ALTER TABLE order_bundle ADD CONSTRAINT prov_ordbund_id_key UNIQUE (prov_order_id, provider_id);
ALTER TABLE order_bundle ADD CONSTRAINT tran_ordbund_id_key UNIQUE (transaction_id, provider_id);
ALTER TABLE order_bundle ADD CONSTRAINT trck_ordbund_id_key UNIQUE (tracking_code, provider_id);
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_prov_fk
    FOREIGN KEY (provider_id)
    REFERENCES provider;
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_sell_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
CREATE SEQUENCE order_bundle_id_seq;
ALTER TABLE orders ADD COLUMN order_bundle_id int8;
ALTER TABLE orders
    ADD CONSTRAINT ord_ordbund_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
alter table orders alter column order_bundle_id set not null;
ALTER TABLE orders DROP COLUMN prov_order_id;
ALTER TABLE orders DROP COLUMN tracking_code;
ALTER TABLE orders DROP COLUMN transaction_id;
ALTER TABLE orders DROP COLUMN customer_id;
ALTER TABLE orders DROP COLUMN provider_id;
ALTER TABLE orders DROP COLUMN seller_id;
ALTER TABLE orders DROP COLUMN last_cust_update_sent;
ALTER TABLE orders DROP COLUMN first_name;
ALTER TABLE orders DROP COLUMN last_name;
ALTER TABLE orders DROP COLUMN address;
ALTER TABLE orders DROP COLUMN postcode;
ALTER TABLE orders DROP COLUMN city;


ALTER TABLE order_bundle ADD COLUMN refunded boolean;
ALTER TABLE order_bundle ADD COLUMN cust_shipping_cost_mc numeric(12,6);
ALTER TABLE order_bundle ADD COLUMN cust_shipping_cost_euro numeric(12,6);
ALTER TABLE order_bundle ADD COLUMN targeted_ship_mc numeric(12,6);
ALTER TABLE order_bundle ADD COLUMN prov_ship_pc numeric(12,6);
ALTER TABLE order_bundle ADD COLUMN prov_ship_euro numeric(12,6);
ALTER TABLE customer ADD COLUMN date_kicked timestamp without time zone;
ALTER TABLE customer ADD COLUMN nbr_times_kicked integer;
ALTER TABLE customer ADD COLUMN informed_too_big_pay boolean;
ALTER TABLE orders ADD COLUMN refunding numeric(12,6);
update orders set refunding = 0;
alter table orders alter column refunding set not null;

alter table order_bundle alter column cust_shipping_cost_mc set not null;
alter table order_bundle alter column cust_shipping_cost_euro set not null;
alter table order_bundle alter column targeted_ship_mc set not null;
alter table order_bundle alter column refunded set not null;
alter table customer alter column date_kicked set not null;
alter table customer alter column nbr_times_kicked set not null;
ALTER TABLE orders ALTER COLUMN shipping_cost_mc DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_cost_euro DROP NOT NULL;
alter table customer alter column informed_too_big_pay set not null;

ALTER TABLE orders DROP COLUMN shipping_cost_mc;
ALTER TABLE orders DROP COLUMN shipping_cost_euro;
ALTER TABLE orders DROP COLUMN date_kicked;


ALTER TABLE customer ADD COLUMN conf_code_ptm varchar(255);
ALTER TABLE customer ADD COLUMN account_no_ptm varchar(255);
ALTER TABLE customer ADD CONSTRAINT cust_conf_code_key UNIQUE (conf_code_ptm);
alter table customer alter column conf_code_ptm set not null;


ALTER TABLE order_bundle ADD COLUMN paypal_id varchar(255);
ALTER TABLE order_bundle ADD CONSTRAINT pp_ordbund_id_key UNIQUE (paypal_id, provider_id);


ALTER TABLE order_bundle DROP COLUMN refunded;
ALTER TABLE customer RENAME COLUMN conf_code_ptm TO conf_code;
ALTER TABLE customer RENAME COLUMN account_no_ptm TO account_no;

ALTER TABLE product ADD COLUMN cat_suggestion varchar(1024);


CREATE TABLE tracking (
	id int8 NOT null,
 	tracking_code varchar(255) not null,
	order_bundle_id int8 not null,
	follow boolean not null
);
ALTER TABLE ONLY tracking ADD CONSTRAINT tracking_pkey PRIMARY KEY (id);
CREATE SEQUENCE tracking_id_seq;
ALTER TABLE tracking ADD CONSTRAINT tracking_trcode_key UNIQUE (tracking_code);
ALTER TABLE tracking
    ADD CONSTRAINT trck_ordbund_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
ALTER TABLE orders ADD COLUMN tracking_id int8;
ALTER TABLE orders
    ADD CONSTRAINT ord_track_fk
    FOREIGN KEY (tracking_id)
    REFERENCES tracking;
ALTER TABLE order_bundle DROP CONSTRAINT trck_ordbund_id_key;
ALTER TABLE order_bundle DROP COLUMN tracking_code;


ALTER TABLE ad ADD COLUMN nbr_public_questions integer;
update ad set nbr_public_questions = 0;
alter table ad alter column nbr_public_questions set not null;


ALTER TABLE orders ADD COLUMN resend_loss_euro numeric(12,6);
update orders set resend_loss_euro = 0;
alter table orders alter column resend_loss_euro set not null;
ALTER TABLE order_bundle ADD COLUMN resend_loss_euro numeric(12,6);
update order_bundle set resend_loss_euro = 0;
alter table order_bundle alter column resend_loss_euro set not null;


ALTER TABLE product ADD COLUMN compensation boolean;
alter table product alter column compensation set not null;


CREATE TABLE error_tracking (
	id int8 NOT null,
	command varchar(255) not null,
	market_id int8,
	provider_id int8,
	seller_id int8,
	date timestamp without time zone not null,
	acknowledged boolean not null,
	message varchar(512) not null,
	stacktrace text not null
);
ALTER TABLE ONLY error_tracking ADD CONSTRAINT error_tracking_pkey PRIMARY KEY (id);
CREATE SEQUENCE error_tracking_id_seq;
ALTER TABLE error_tracking
    ADD CONSTRAINT et_ma_fk
    FOREIGN KEY (market_id)
    REFERENCES market;
ALTER TABLE error_tracking
    ADD CONSTRAINT et_pr_fk
    FOREIGN KEY (provider_id)
    REFERENCES provider;
ALTER TABLE error_tracking
    ADD CONSTRAINT et_se_fk
    FOREIGN KEY (seller_id)
    REFERENCES seller;
ALTER TABLE provider DROP COLUMN error;
ALTER TABLE market DROP COLUMN error;
ALTER TABLE seller DROP COLUMN error;


ALTER TABLE error_tracking DROP COLUMN acknowledged;
ALTER TABLE error_tracking ADD COLUMN arguments varchar(255);
ALTER TABLE error_tracking ADD COLUMN acknowledged timestamp without time zone;


CREATE TABLE provider_question (
	id int8 NOT null,
	order_bundle_id int8 not null,
	nbr_messages int not null,
	last_own_comment timestamp without time zone not null,
	last_provider_comment timestamp without time zone,
	operator_last_informed timestamp without time zone,
	open boolean not null,
	provider_ticket_id varchar(255) not null,
	subject varchar(255) not null,
	comment_to_add text,
	contents text not null
);
ALTER TABLE ONLY provider_question ADD CONSTRAINT provider_question_pkey PRIMARY KEY (id);
CREATE SEQUENCE provider_question_id_seq;
ALTER TABLE provider_question ADD CONSTRAINT provquest_bundle_key UNIQUE (order_bundle_id);
ALTER TABLE provider_question
    ADD CONSTRAINT provquest_bundle_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
ALTER TABLE email ADD COLUMN order_bundle_id int8;
ALTER TABLE email
    ADD CONSTRAINT email_bundle_fk
    FOREIGN KEY (order_bundle_id)
    REFERENCES order_bundle;
CREATE INDEX email_ob_index ON email (order_bundle_id);


ALTER TABLE tracking ADD COLUMN follow boolean;
update tracking set follow = true;
alter table tracking alter column follow set not null;


ALTER TABLE provider_question DROP CONSTRAINT provquest_bundle_key;
ALTER TABLE provider_question ADD CONSTRAINT provquest_provti_key UNIQUE (provider_ticket_id);


ALTER TABLE login_name ADD CONSTRAINT lg_malo_key UNIQUE (market_id, login_name);


CREATE TABLE currency (
	id int8 NOT null,
	name varchar(16) not null,
	symbol varchar(16) not null,
	exrate_euro numeric(12,6) not null,
	last_updated timestamp without time zone not null
);
CREATE SEQUENCE currency_id_seq;
ALTER TABLE ONLY currency ADD CONSTRAINT curr_pkey PRIMARY KEY (id);
ALTER TABLE market DROP COLUMN currency;
ALTER TABLE market ADD COLUMN currency_id int8;
update market set currency_id = 1;
alter table market alter column currency_id set not null;
ALTER TABLE market
    ADD CONSTRAINT mar_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
ALTER TABLE provider DROP COLUMN currency;
ALTER TABLE provider ADD COLUMN currency_id int8;
update provider set currency_id = 2;
alter table provider alter column currency_id set not null;
ALTER TABLE provider
    ADD CONSTRAINT prov_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
ALTER TABLE transaction ADD COLUMN currency_id int8;
update transaction set currency_id = 1;
alter table transaction alter column currency_id set not null;
ALTER TABLE transaction
    ADD CONSTRAINT tra_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
ALTER TABLE order_bundle ADD COLUMN pcurrency_id int8;
update order_bundle set pcurrency_id = 2;
alter table order_bundle alter column pcurrency_id set not null;
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_pcur_fk
    FOREIGN KEY (pcurrency_id)
    REFERENCES currency;

    
ALTER TABLE order_bundle ADD COLUMN ccurrency_id int8;
update order_bundle set ccurrency_id = 1;
alter table order_bundle alter column ccurrency_id set not null;
ALTER TABLE order_bundle
    ADD CONSTRAINT ordbund_ccur_fk
    FOREIGN KEY (ccurrency_id)
    REFERENCES currency;


ALTER TABLE orders RENAME COLUMN refunding TO refunding_mc;
ALTER TABLE seller ADD COLUMN ship_cost_multiplier numeric(12,6);
update seller set ship_cost_multiplier = 1.1;
alter table seller alter column ship_cost_multiplier set not null;
ALTER TABLE provider ADD COLUMN location varchar(32);
update provider set location = 'Hong Kong';
alter table provider alter column location set not null;
ALTER TABLE market ADD COLUMN max_bundle_size_mc numeric(12,6);
update market set max_bundle_size_mc = 41.0 where name <> 'Google-Order';
ALTER TABLE order_bundle ADD COLUMN max_bundle_size_mc numeric(12,6);
update order_bundle set max_bundle_size_mc = 41.0;
alter table order_bundle alter column max_bundle_size_mc set not null;
ALTER TABLE seller_market ADD COLUMN min_bid_increase_mc numeric(12,6);
update seller_market set min_bid_increase_mc = 0.10;
alter table seller_market alter column min_bid_increase_mc set not null;
ALTER TABLE adtemplate DROP COLUMN min_bid_increase_mc;
ALTER TABLE adtemplate DROP COLUMN min_bid_increase_euro;


ALTER TABLE ad RENAME COLUMN price_euro TO buyout_price_euro;
ALTER TABLE ad ADD COLUMN buyout_price_mc numeric(12,6);
update ad set buyout_price_mc = buyout_price_euro;
alter table ad alter column buyout_price_mc set not null;


ALTER TABLE seller ADD COLUMN customer_vat numeric(12,6);
update seller set customer_vat = 0;
alter table seller alter column customer_vat set not null;
ALTER TABLE orders RENAME COLUMN fin_sp_mc TO fin_sp_mc_inc_vat;
ALTER TABLE orders RENAME COLUMN fin_sp_euro TO fin_sp_euro_inc_vat;
ALTER TABLE orders RENAME COLUMN fin_pp_pc TO fin_pp_pc_inc_vat;
ALTER TABLE orders RENAME COLUMN fin_pp_euro TO fin_pp_euro_inc_vat;
ALTER TABLE orders RENAME COLUMN resend_loss_euro TO resend_loss_euro_inc_vat;
ALTER TABLE order_bundle RENAME COLUMN resend_loss_euro TO resend_loss_euro_inc_vat;
ALTER TABLE order_bundle RENAME COLUMN cust_shipping_cost_mc TO cust_shipping_cost_mc_inc_vat;
ALTER TABLE order_bundle RENAME COLUMN cust_shipping_cost_euro TO cust_shipping_cost_euro_inc_vat;
ALTER TABLE order_bundle RENAME COLUMN prov_ship_pc TO prov_ship_pc_inc_vat;
ALTER TABLE order_bundle RENAME COLUMN prov_ship_euro TO prov_ship_euro_inc_vat;
ALTER TABLE orders ADD COLUMN fin_pp_euro_exc_vat numeric(12,6);
update orders set fin_pp_euro_exc_vat = fin_pp_euro_inc_vat;
ALTER TABLE orders ADD COLUMN fin_pp_pc_exc_vat numeric(12,6);
update orders set fin_pp_pc_exc_vat = fin_pp_pc_inc_vat;
ALTER TABLE orders ADD COLUMN fin_sp_euro_exc_vat numeric(12,6);
update orders set fin_sp_euro_exc_vat = fin_sp_euro_inc_vat;
alter table orders alter column fin_sp_euro_exc_vat set not null;
ALTER TABLE orders ADD COLUMN fin_sp_mc_exc_vat numeric(12,6);
update orders set fin_sp_mc_exc_vat = fin_sp_mc_inc_vat;
alter table orders alter column fin_sp_mc_exc_vat set not null;
ALTER TABLE orders ADD COLUMN customer_vat numeric(12,6);
update orders set customer_vat = 0;
alter table orders alter column customer_vat set not null;
ALTER TABLE orders ADD COLUMN provider_vat numeric(12,6);
update orders set provider_vat = 0;
alter table orders alter column provider_vat set not null;
ALTER TABLE orders ADD COLUMN provider_resend_vat numeric(12,6);
update orders set provider_resend_vat = 0;
alter table orders alter column provider_resend_vat set not null;
ALTER TABLE orders ADD COLUMN resend_loss_euro_exc_vat numeric(12,6);
update orders set resend_loss_euro_exc_vat = resend_loss_euro_inc_vat;
alter table orders alter column resend_loss_euro_exc_vat set not null;
ALTER TABLE order_bundle ADD COLUMN cust_shipping_cost_mc_exc_vat numeric(12,6);
update order_bundle set cust_shipping_cost_mc_exc_vat = cust_shipping_cost_mc_inc_vat;
alter table order_bundle alter column cust_shipping_cost_mc_exc_vat set not null;
ALTER TABLE order_bundle ADD COLUMN cust_shipping_cost_euro_exc_vat numeric(12,6);
update order_bundle set cust_shipping_cost_euro_exc_vat = cust_shipping_cost_euro_inc_vat;
alter table order_bundle alter column cust_shipping_cost_euro_exc_vat set not null;
ALTER TABLE order_bundle ADD COLUMN prov_ship_pc_exc_vat numeric(12,6);
update order_bundle set prov_ship_pc_exc_vat = prov_ship_pc_inc_vat;
ALTER TABLE order_bundle ADD COLUMN prov_ship_euro_exc_vat numeric(12,6);
update order_bundle set prov_ship_euro_exc_vat = prov_ship_euro_inc_vat;
ALTER TABLE order_bundle ADD COLUMN customer_ship_vat numeric(12,6);
update order_bundle set customer_ship_vat = 0;
alter table order_bundle alter column customer_ship_vat set not null;
ALTER TABLE order_bundle ADD COLUMN provider_ship_vat numeric(12,6);
update order_bundle set provider_ship_vat = 0;
alter table order_bundle alter column provider_ship_vat set not null;
ALTER TABLE order_bundle ADD COLUMN provider_ship_resend_vat numeric(12,6);
update order_bundle set provider_ship_resend_vat = 0;
alter table order_bundle alter column provider_ship_resend_vat set not null;
ALTER TABLE order_bundle ADD COLUMN resend_loss_euro_exc_vat numeric(12,6);
update order_bundle set resend_loss_euro_exc_vat = resend_loss_euro_inc_vat;
alter table order_bundle alter column resend_loss_euro_exc_vat set not null;
ALTER TABLE provider ADD COLUMN vat numeric(12,6);
update provider set vat = 0;
alter table provider alter column vat set not null;


ALTER TABLE seller_market RENAME COLUMN ship_cost_mark_curr TO ship_cost_mark_curr_inc_vat;


ALTER TABLE ad RENAME COLUMN shipping_cost_mc TO shipping_cost_mc_inc_vat;
ALTER TABLE ad RENAME COLUMN shipping_cost_euro TO shipping_cost_euro_inc_vat;
ALTER TABLE orders ALTER COLUMN provider_vat DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN provider_resend_vat DROP NOT NULL;
ALTER TABLE order_bundle ALTER COLUMN provider_ship_vat DROP NOT NULL;
ALTER TABLE order_bundle ALTER COLUMN provider_ship_resend_vat DROP NOT NULL;


ALTER TABLE orders RENAME COLUMN customer_vat TO customer_vat_perc;
ALTER TABLE seller RENAME COLUMN customer_vat TO customer_vat_perc;
ALTER TABLE order_bundle RENAME COLUMN max_bundle_size_mc TO max_bundle_size_mc_exc_vat;
ALTER TABLE order_bundle RENAME COLUMN customer_ship_vat TO customer_ship_vat_perc;
ALTER TABLE market RENAME COLUMN max_bundle_size_mc TO max_bundle_size_mc_exc_vat;
ALTER TABLE seller_market RENAME COLUMN min_bid_increase_mc TO min_bid_increase_mc_inc_vat;
ALTER TABLE ad RENAME COLUMN start_price_mc TO start_price_mc_inc_vat;
ALTER TABLE ad RENAME COLUMN start_price_euro TO start_price_euro_inc_vat;
ALTER TABLE ad RENAME COLUMN buyout_price_mc TO buyout_price_mc_inc_vat;
ALTER TABLE ad RENAME COLUMN buyout_price_euro TO buyout_price_euro_inc_vat;
ALTER TABLE ad RENAME COLUMN min_selling_price_mc TO min_selling_price_mc_inc_vat;
ALTER TABLE ad RENAME COLUMN min_selling_price_euro TO min_selling_price_euro_inc_vat;
ALTER TABLE ad RENAME COLUMN prov_price_at_listing_pc TO prov_price_at_listing_pc_inc_vat;
ALTER TABLE ad RENAME COLUMN prov_price_at_listing_euro TO prov_price_at_listing_euro_inc_vat;
ALTER TABLE orders RENAME COLUMN provider_vat TO provider_vat_perc;
ALTER TABLE orders RENAME COLUMN provider_resend_vat TO provider_resend_vat_perc;
ALTER TABLE order_bundle RENAME COLUMN provider_ship_vat TO provider_ship_vat_perc;
ALTER TABLE order_bundle RENAME COLUMN provider_ship_resend_vat TO provider_ship_resend_vat_perc;
ALTER TABLE provider RENAME COLUMN vat TO vat_perc;


ALTER TABLE orders ADD COLUMN transfered_to_verific boolean;
update orders set transfered_to_verific = true;
update orders set transfered_to_verific = false where targeted_mc < fin_sp_mc_inc_vat;
alter table orders alter column transfered_to_verific set not null;
ALTER TABLE orders ADD COLUMN old_verification boolean;
update orders set old_verification = true;
update orders set old_verification = false where transfered_to_verific = false;
alter table orders alter column old_verification set not null;

ALTER TABLE order_bundle ADD COLUMN transfered_to_verific boolean;
update order_bundle set transfered_to_verific = true;
update order_bundle set transfered_to_verific = false where targeted_ship_mc < cust_shipping_cost_mc_inc_vat;
alter table order_bundle alter column transfered_to_verific set not null;
ALTER TABLE order_bundle ADD COLUMN old_verification boolean;
update order_bundle set old_verification = true;
update order_bundle set old_verification = false where transfered_to_verific = false;
alter table order_bundle alter column old_verification set not null;

ALTER TABLE transaction ADD COLUMN transfered_to_verific boolean;
update transaction set transfered_to_verific = true;
alter table transaction alter column transfered_to_verific set not null;
ALTER TABLE transaction ADD COLUMN old_verification boolean;
update transaction set old_verification = true;
alter table transaction alter column old_verification set not null;

CREATE TABLE verification (
	id int8 NOT null,
	vat_perc numeric(12,6) not null,
	currency_id int8 not null,
	tot_sp_inc_vat numeric(12,6) not null,
	tot_sp_exc_vat numeric(12,6) not null,
	tot_sp_inc_vat_corr numeric(12,6) not null,
	tot_sp_exc_vat_corr numeric(12,6) not null
);
CREATE SEQUENCE verification_id_seq;
ALTER TABLE ONLY verification ADD CONSTRAINT verif_pkey PRIMARY KEY (id);
ALTER TABLE verification
    ADD CONSTRAINT verif_cur_fk
    FOREIGN KEY (currency_id)
    REFERENCES currency;
ALTER TABLE verification ADD CONSTRAINT perc_curr_id_key UNIQUE (vat_perc, currency_id);


ALTER TABLE market ADD COLUMN active boolean;
update market set active = true;
alter table market alter column active set not null;


ALTER TABLE product ADD COLUMN provider_url varchar(255);
update product set provider_url = 'http://www.dealextreme.com/details.dx/' || provider_prod_id where provider_id = 1;
update product set provider_url = 'http://www.focalprice.com/' || provider_prod_id || '/something.html' where provider_id = 2;
alter table product alter column provider_url set not null;
ALTER TABLE product ADD CONSTRAINT product_url_key UNIQUE (provider_url);


ALTER TABLE orders ADD COLUMN product_id int8;
update orders o set product_id = (select id from product p where p.provider_prod_id = o.provider_prod_id);
alter table orders alter column product_id set not null;
ALTER TABLE orders DROP COLUMN provider_prod_id;


ALTER TABLE market DROP COLUMN max_bundle_size_mc_exc_vat;
ALTER TABLE seller_market ADD COLUMN max_bundle_size_mc_exc_vat numeric(12,6);
update seller_market set max_bundle_size_mc_exc_vat = 41.0;
alter table seller_market alter column max_bundle_size_mc_exc_vat set not null;


ALTER TABLE product ADD COLUMN min_prov_ship_cost_pc_inc_vat numeric(12,6);
update product set min_prov_ship_cost_pc_inc_vat = 0;
alter table product alter column min_prov_ship_cost_pc_inc_vat set not null;
ALTER TABLE market DROP COLUMN active;
ALTER TABLE seller DROP COLUMN active;
ALTER TABLE system_email ADD COLUMN check_email boolean;
update system_email set check_email = true;
alter table system_email alter column check_email set not null;
ALTER TABLE seller_market ADD COLUMN do_listing boolean;
update seller_market set do_listing = false;
update seller_market set do_listing = true where id = 4;
alter table seller_market alter column do_listing set not null;
ALTER TABLE seller_market RENAME COLUMN lister_bean TO ad_decorator;
ALTER TABLE seller_market RENAME COLUMN ship_cost_mark_curr_inc_vat TO min_ship_cost_mark_curr_inc_vat;


ALTER TABLE seller_market ADD COLUMN buyout_multiplier numeric(12,6);
update seller_market set buyout_multiplier = 1.1;
alter table seller_market alter column buyout_multiplier set not null;


ALTER TABLE orders ADD COLUMN google_order boolean;
update orders set google_order = true where ad_id is null;
update orders set google_order = false where ad_id is not null;
alter table orders alter column google_order set not null;
alter table orders alter column ad_id set not null;
ALTER TABLE ad ADD COLUMN original_amount integer;
update ad set original_amount = 1;
alter table ad alter column original_amount set not null;
ALTER TABLE ad ADD COLUMN bought_amount integer;
update ad set bought_amount = 0;
alter table ad alter column bought_amount set not null;


ALTER TABLE adtemplate ADD COLUMN visibility_type integer;
update adtemplate set visibility_type = 0;
alter table adtemplate alter column visibility_type set not null;
ALTER TABLE ad ADD COLUMN original_ad_id int8;
ALTER TABLE ad
    ADD CONSTRAINT ad_ad_fk
    FOREIGN KEY (original_ad_id)
    REFERENCES ad;


ALTER TABLE ad ADD COLUMN visibility_type integer;
update ad set visibility_type = 0;
alter table ad alter column visibility_type set not null;


ALTER TABLE ad ADD COLUMN open_days integer;
update ad a set open_days = (select open_days from adtemplate at where at.id = a.ad_template_id);
alter table ad alter column open_days set not null;
ALTER TABLE ad ADD COLUMN advert_cost_euro_inc_vat numeric(12,6);
update ad set advert_cost_euro_inc_vat = 0;
alter table ad alter column advert_cost_euro_inc_vat set not null;


ALTER TABLE ad ADD COLUMN advert_cost_euro_exc_vat numeric(12,6);
update ad set advert_cost_euro_exc_vat = 0;
alter table ad alter column advert_cost_euro_exc_vat set not null;


ALTER TABLE ad ADD COLUMN transfered_to_verific boolean;
update ad set transfered_to_verific = false;
alter table ad alter column transfered_to_verific set not null;


ALTER TABLE product ADD COLUMN status_fetch_date timestamp without time zone;
ALTER TABLE product ADD COLUMN curr_prov_price_pc_inc_vat numeric(12,6);
ALTER TABLE product ADD COLUMN product_state integer;
update product set product_state = 0;
alter table product alter column product_state set not null;
ALTER TABLE product ADD COLUMN not_found_counter integer;
update product set not_found_counter = 0;
alter table product alter column not_found_counter set not null;
ALTER TABLE adtemplate ADD COLUMN inactive_reason varchar(255);
update adtemplate set inactive_reason = 'old inactive' where active = false;


ALTER TABLE product ADD COLUMN manufacturer_url varchar(255);
ALTER TABLE product ADD CONSTRAINT manufacturer_url_key UNIQUE (manufacturer_url);


ALTER TABLE order_bundle ADD COLUMN comment varchar(512);


ALTER TABLE product ADD COLUMN offline_sale boolean;
update product set offline_sale = false;
alter table product alter column offline_sale set not null;


ALTER TABLE product DROP COLUMN listing_failed;


-- JUST CREATED
ALTER TABLE email ADD COLUMN customer_id int8;
ALTER TABLE email
    ADD CONSTRAINT em_cu_fk
    FOREIGN KEY (customer_id)
    REFERENCES customer;
ALTER TABLE email ADD COLUMN priority integer;
update email set priority = 10;
-- alter table email alter column priority set not null; //TODO: run this one (requires deployment of new google-order and posttracker)
