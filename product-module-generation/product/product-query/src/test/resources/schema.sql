CREATE SCHEMA IF NOT EXISTS homebase_db;

DROP TABLE IF EXISTS homebase_db.products;

CREATE TABLE homebase_db.products (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    secondary_description TEXT,
    category VARCHAR(50),
    fabric VARCHAR(50),
    weave_type VARCHAR(50),
    size VARCHAR(50),
    length_meters DOUBLE,
    blouse_piece BOOLEAN,
    mrp BIGINT,
    selling_price BIGINT,
    discount_pct INT,
    stock INT,
    images VARCHAR(2000),
    video_url VARCHAR(1000),
    video_status VARCHAR(50),
    hsn_code VARCHAR(50),
    gst_pct INT,
    average_rating DOUBLE,
    review_count INT,
    weight_grams INT,
    width_inches DOUBLE,
    blouse_length_meters DOUBLE,
    occasion VARCHAR(255),
    work_type VARCHAR(255),
    pattern VARCHAR(255),
    body_color VARCHAR(100),
    border_color VARCHAR(100),
    pallu_color VARCHAR(100),
    care_instructions VARCHAR(500),
    certification VARCHAR(255),
    sku VARCHAR(100) UNIQUE,
    tags VARCHAR(500),
    is_active BOOLEAN,
    is_deleted BOOLEAN
);

-- Inserts matching the feature file's expectations (names, IDs)
INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('1', 'James', 'Chennai', 'SILK', 'HANDLOOM', 4500, 5000, 10, true, false, 'SKU-0001');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('2', 'John', 'Bangalore', 'COTTON', 'IKAT', 5600, 6000, 10, true, false, 'SKU-0002');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('3', 'Mohan', 'Delhi', 'SILK', 'HANDLOOM', 7900, 8000, 10, true, false, 'SKU-0003');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('4', 'Amit', 'Mumbai', 'LINEN', 'PLAIN', 7600, 8000, 10, true, false, 'SKU-0004');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('5', 'Akash', 'Bangalore', 'COTTON', 'IKAT', 6600, 7000, 10, true, false, 'SKU-0005');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('6', 'Manjunath', 'Chennai', 'SILK', 'HANDLOOM', 6300, 7000, 10, true, false, 'SKU-0006');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('7', 'Kathy', 'Bangalore', 'SILK', 'IKAT', 5900, 6000, 10, true, false, 'SKU-0007');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('8', 'Kamala', 'Gurgaon', 'SILK', 'HANDLOOM', 8000, 9000, 10, true, false, 'SKU-0008');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('9', 'Sudha', 'Kolkata', 'SILK', 'HANDLOOM', 4500, 5000, 10, true, false, 'SKU-0009');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('10', 'Rajesh', 'Gulbarga', 'COTTON', 'PLAIN', 2100, 3000, 10, true, false, 'SKU-0010');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('11', 'Srinivas', 'Chandigarh', 'SILK', 'HANDLOOM', 7700, 8000, 10, true, false, 'SKU-0011');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('12', 'Sam', 'Hyderabad', 'COTTON', 'IKAT', 7600, 8000, 10, true, false, 'SKU-0012');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('13', 'Gurupreet', 'Delhi', 'LINEN', 'PLAIN', 4500, 5000, 10, true, false, 'SKU-0013');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('14', 'Rashmi', 'Mumbai', 'SILK', 'HANDLOOM', 9000, 10000, 10, true, false, 'SKU-0014');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('15', 'Rahul', 'Delhi', 'SILK', 'HANDLOOM', 4800, 5000, 10, true, false, 'SKU-0015');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('16', 'Gopi', 'Trivandrum', 'COTTON', 'IKAT', 9900, 10000, 10, true, false, 'SKU-0016');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('17', 'Honey', 'Delhi', 'SILK', 'HANDLOOM', 7300, 8000, 10, true, false, 'SKU-0017');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('18', 'Vikas', 'Bangalore', 'COTTON', 'IKAT', 6500, 7000, 10, true, false, 'SKU-0018');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('19', 'Phani', 'Chennai', 'SILK', 'HANDLOOM', 5200, 6000, 10, true, false, 'SKU-0019');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('20', 'Ayush', 'Hyderabad', 'COTTON', 'IKAT', 4800, 5000, 10, true, false, 'SKU-0020');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('21', 'Siddharth', 'Jaipur', 'LINEN', 'PLAIN', 9700, 10000, 10, true, false, 'SKU-0021');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('22', 'Damodar', 'Delhi', 'SILK', 'HANDLOOM', 1200, 2000, 10, true, false, 'SKU-0022');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('23', 'Sundar', 'Chennai', 'SILK', 'HANDLOOM', 4500, 5000, 10, true, false, 'SKU-0023');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('24', 'Chetan', 'Mumbai', 'SILK', 'HANDLOOM', 1900, 2000, 10, true, false, 'SKU-0024');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('25', 'Narendra', 'Mumbai', 'COTTON', 'IKAT', 7000, 8000, 10, true, false, 'SKU-0025');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('26', 'Shankuntala', 'Bangalore', 'SILK', 'HANDLOOM', 2300, 3000, 10, true, false, 'SKU-0026');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('27', 'Sohan', 'Chennai', 'SILK', 'HANDLOOM', 5000, 6000, 10, true, false, 'SKU-0027');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('28', 'Roshan', 'Delhi', 'SILK', 'HANDLOOM', 6900, 8000, 10, true, false, 'SKU-0028');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('29', 'Vijay', 'Bangalore', 'COTTON', 'IKAT', 4300, 5000, 10, true, false, 'SKU-0029');

INSERT INTO homebase_db.products (id, name, body_color, fabric, weave_type, selling_price, mrp, stock, is_active, is_deleted, sku) 
VALUES ('30', 'Brijesh', 'Hyderabad', 'COTTON', 'IKAT', 6500, 7000, 10, true, false, 'SKU-0030');