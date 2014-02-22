# Homework schema
 
# --- !Ups

create sequence product_id_seq;
create table barcode (
    barcode text,
    product_id integer
);

create table product (
  id integer not null DEFAULT nextval('product_id_seq'),
  name text,
  price float,
  bought float
);

create table purchases (
	purchase_date date,
	product_id integer,
	quantity integer
);

# --- !Downs

drop sequence product_id_seq;
drop table barcode;
drop table product;
drop table purchases;