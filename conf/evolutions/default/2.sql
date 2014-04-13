# Student Store schema
 
# --- !Ups

ALTER TABLE barcode
ADD mode text;

ALTER TABLE product
ADD mode text;

ALTER TABLE purchases
ADD mode text;

UPDATE barcode
SET mode = 'Student Store';

UPDATE product
SET mode = 'Student Store';

UPDATE purchases
SET mode = 'Student Store';

CREATE INDEX on purchases (mode);
CREATE INDEX on purchases (purchase_date);

# --- !Downs

ALTER TABLE barcode
DROP COLUMN mode;

ALTER TABLE product
DROP COLUMN mode;

ALTER TABLE purchases
DROP COLUMN mode;