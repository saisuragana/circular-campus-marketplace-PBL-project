-- ============================================================
-- Circular Campus Marketplace - Resale, Cart & Wallet
-- Database script for MySQL (XAMPP / phpMyAdmin)
-- Import this file in phpMyAdmin -> Import, or run:
--   mysql -u root < campus_marketplace.sql
-- ============================================================

DROP DATABASE IF EXISTS campus_marketplace_final;
CREATE DATABASE campus_marketplace_final
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE campus_marketplace_final;

-- ------------------------------------------------------------
-- 1. STUDENTS
-- Passwords are stored as SHA-256 hex digests (never plain text).
-- The Java code hashes the typed password and compares digests.
-- ------------------------------------------------------------
CREATE TABLE students (
  student_id          INT PRIMARY KEY AUTO_INCREMENT,
  name                VARCHAR(100) NOT NULL,
  email               VARCHAR(100) UNIQUE NOT NULL,
  wallet_balance      DECIMAL(10,2) DEFAULT 1000.00,
  password            VARCHAR(255) NOT NULL,
  verification_status VARCHAR(20) DEFAULT 'Pending',
  role                VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
  created_at          DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. LISTINGS
-- NOTE: `condition` is a reserved word in MySQL, so it is always
-- written inside backticks - both here and in the Java SQL.
-- ------------------------------------------------------------
CREATE TABLE listings (
  listing_id        INT PRIMARY KEY AUTO_INCREMENT,
  seller_id         INT NOT NULL,
  title             VARCHAR(150) NOT NULL,
  category          VARCHAR(50) NOT NULL,
  price             DECIMAL(10,2) NOT NULL,
  `condition`       VARCHAR(50) NOT NULL,
  status            VARCHAR(20) DEFAULT 'Available',
  description       TEXT,
  keywords          VARCHAR(255),
  quantity          INT DEFAULT 1,
  validation_status VARCHAR(20) DEFAULT 'Auto Checked',
  validation_score INT NOT NULL DEFAULT 0,
  validation_reason VARCHAR(500) NULL,
  reviewed_by INT NULL,
  reviewed_at DATETIME NULL,
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_listing_seller FOREIGN KEY (seller_id)
      REFERENCES students(student_id)
) ENGINE=InnoDB;

CREATE INDEX idx_listings_category ON listings(category);
CREATE INDEX idx_listings_status   ON listings(status);

-- ------------------------------------------------------------
-- 3. CART_ITEMS
-- A row belongs either to a guest (session_id set, student_id NULL)
-- or to a logged-in student (student_id set).
-- ------------------------------------------------------------
CREATE TABLE cart_items (
  cart_id    INT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(100),
  student_id INT NULL,
  listing_id INT NOT NULL,
  quantity   INT DEFAULT 1,
  added_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cart_student FOREIGN KEY (student_id)
      REFERENCES students(student_id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_listing FOREIGN KEY (listing_id)
      REFERENCES listings(listing_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cart_session ON cart_items(session_id);
CREATE INDEX idx_cart_student ON cart_items(student_id);

-- ------------------------------------------------------------
-- 4. TRANSACTIONS
-- ------------------------------------------------------------
CREATE TABLE transactions (
  txn_id     INT PRIMARY KEY AUTO_INCREMENT,
  buyer_id   INT NOT NULL,
  seller_id  INT NOT NULL,
  listing_id INT NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  payment_method VARCHAR(30) NOT NULL DEFAULT 'CAMPUS_WALLET',
  payment_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  payment_reference VARCHAR(60) NULL,
  quantity   INT DEFAULT 1,
  order_ref VARCHAR(40) NULL,
  txn_date   DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_txn_buyer   FOREIGN KEY (buyer_id)   REFERENCES students(student_id),
  CONSTRAINT fk_txn_seller  FOREIGN KEY (seller_id)  REFERENCES students(student_id),
  CONSTRAINT fk_txn_listing FOREIGN KEY (listing_id) REFERENCES listings(listing_id)
) ENGINE=InnoDB;

CREATE INDEX idx_transactions_order_ref ON transactions(order_ref);

-- ------------------------------------------------------------
-- 5. LISTING_IMAGES
-- Multiple product images are stored outside the database; this
-- table stores their safe server-side filenames and listing link.
-- ------------------------------------------------------------
CREATE TABLE listing_images (
  image_id      INT PRIMARY KEY AUTO_INCREMENT,
  listing_id    INT NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  original_name VARCHAR(255),
  uploaded_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_listing_images_listing
      FOREIGN KEY (listing_id) REFERENCES listings(listing_id)
      ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_listing_images_listing
  ON listing_images(listing_id);

-- ------------------------------------------------------------
-- 6. PRODUCT_REPORTS
-- Buyer reports are linked to the real transaction and listing.
-- There is no admin/middleman workflow in this project.
-- ------------------------------------------------------------
CREATE TABLE product_reports (
  report_id           INT PRIMARY KEY AUTO_INCREMENT,
  txn_id              INT NOT NULL,
  listing_id          INT NOT NULL,
  buyer_id            INT NOT NULL,
  seller_id           INT NOT NULL,
  reason              VARCHAR(100) NOT NULL,
  description         TEXT NOT NULL,
  evidence_file_name  VARCHAR(255) NULL,
  status              VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  resolution_note     VARCHAR(500) NULL,
  reviewed_by        INT NULL,
  created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
  reviewed_at         DATETIME NULL,
  CONSTRAINT fk_report_txn
      FOREIGN KEY (txn_id) REFERENCES transactions(txn_id),
  CONSTRAINT fk_report_listing
      FOREIGN KEY (listing_id) REFERENCES listings(listing_id),
  CONSTRAINT fk_report_buyer
      FOREIGN KEY (buyer_id) REFERENCES students(student_id),
  CONSTRAINT fk_report_seller
      FOREIGN KEY (seller_id) REFERENCES students(student_id)
) ENGINE=InnoDB;

CREATE INDEX idx_product_reports_status
  ON product_reports(status);

-- ============================================================
-- SAMPLE STUDENTS
-- student_id 1 is the test account used for the demo.
--   email: student@test.com   password: 123456
-- Other accounts (for seller names) use the passwords in the README.
-- ============================================================
INSERT INTO students (name, email, wallet_balance, password, verification_status) VALUES
('Test Student',   'student@test.com', 1000.00, '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Verified'),
('Rahul Verma',    'rahul@test.com',   1500.00, '687f6da20de59091e67f594827cdee268125feb3aed1c3bad77d0f6761eb198a', 'Verified'),
('Priya Nair',     'priya@test.com',   1200.00, '0a8b8dfad3f637d7d30fed7b108c5c5986c4775d14cab26ec9279866eba99116', 'Verified'),
('Arjun Sharma',   'arjun@test.com',    900.00, '572350dbd3dca1b229beee04f34710f1b9c0b71bb1da4ba8fda3161a85109a73', 'Verified');

-- ============================================================
-- SAMPLE LISTINGS (realistic Indian college pricing, in rupees)
-- Sellers are students 2, 3 and 4 so that the test account (1)
-- can buy everything without hitting the "own listing" rule.
-- ============================================================
INSERT INTO listings (seller_id, title, category, price, `condition`, status, description, keywords, quantity, validation_status) VALUES
(2, 'Java Programming Textbook',      'Books',             350.00, 'Good',      'Available', 'Complete Reference style Java textbook used for one semester. All chapters intact, a few pencil notes in the OOP chapter.', 'java, programming, textbook, oop', 3, 'Validated'),
(3, 'Engineering Mathematics Book',   'Books',             280.00, 'Good',      'Available', 'Higher Engineering Mathematics, first year syllabus. Cover slightly worn, pages clean.', 'maths, mathematics, textbook, first year', 2, 'Validated'),
(2, 'Computer Networks Textbook',     'Books',             420.00, 'Like New',  'Available', 'Barely opened. Covers OSI, TCP/IP, routing and the full CN lab syllabus.', 'networks, cn, textbook, tcp', 1, 'Validated'),
(4, 'Used Scientific Calculator',     'Electronics',       700.00, 'Good',      'Available', 'Casio style 991 scientific calculator. Battery replaced last month, display is bright and clear.', 'calculator, scientific, exam, maths', 2, 'Validated'),
(3, 'Dell Inspiron Laptop',           'Electronics',     18500.00, 'Fair',      'Available', 'i5 8th gen, 8GB RAM, 512GB SSD. Battery gives around 2 hours. Charger included. Good for coding labs.', 'laptop, dell, computer, i5', 1, 'Validated'),
(2, 'Mechanical Keyboard',            'Electronics',      1450.00, 'Like New',  'Available', 'Red switch 87 key mechanical keyboard. Detachable USB-C cable included.', 'keyboard, mechanical, computer, typing', 1, 'Validated'),
(4, 'Bluetooth Headphones',           'Electronics',       900.00, 'Good',      'Available', 'Over ear Bluetooth headphones, roughly 18 hours of playback. Ear cushions are intact.', 'headphones, bluetooth, audio, music', 2, 'Validated'),
(3, 'Arduino Uno R3 Board',           'Electronics',       550.00, 'Good',      'Available', 'Genuine Arduino Uno with USB cable. Used for one mini project, fully working.', 'arduino, uno, microcontroller, project', 4, 'Validated'),
(2, 'ESP32 Development Board',        'Electronics',       480.00, 'Like New',  'Available', 'ESP32 WROOM 32 with WiFi and Bluetooth. Never soldered, pins are clean.', 'esp32, iot, wifi, board', 3, 'Validated'),
(4, 'Drawing Tablet',                 'Electronics',      2200.00, 'Good',      'Available', 'Small graphics tablet with pen and two extra nibs. Works with Windows and Linux.', 'tablet, drawing, design, stylus', 1, 'Validated'),
(3, 'Hostel Study Lamp',              'Hostel Essentials', 320.00, 'Good',      'Available', 'Clamp style LED study lamp with three brightness levels. Perfect for a hostel bunk.', 'lamp, study, led, hostel', 5, 'Validated'),
(2, 'Foldable Study Table',           'Hostel Essentials', 650.00, 'Fair',      'Available', 'Wooden foldable laptop table for bed use. One hinge is a little loose but stable.', 'table, study, hostel, laptop', 2, 'Validated'),
(4, 'Electric Kettle 1.5L',           'Hostel Essentials', 540.00, 'Good',      'Available', 'Stainless steel kettle, auto cut off works. Great for Maggi and tea in the hostel.', 'kettle, hostel, kitchen, electric', 2, 'Validated'),
(3, 'College Backpack',               'Accessories',       500.00, 'Good',      'Available', '30 litre backpack with a padded laptop sleeve. All zips work, no tears.', 'backpack, bag, laptop, college', 3, 'Validated'),
(2, 'Hero Sprint Bicycle',            'Sports',           4500.00, 'Fair',      'Available', 'Single speed cycle used for campus commuting. New tyres, brakes recently serviced.', 'bicycle, cycle, fitness, commute', 1, 'Validated'),
(4, 'Badminton Racket Pair',          'Sports',            850.00, 'Good',      'Available', 'Two rackets with a cover and three shuttles. Strings are in good tension.', 'badminton, racket, sports, shuttle', 2, 'Validated'),
(3, 'Drafting Kit and Drawing Board', 'Other',             620.00, 'Good',      'Available', 'First year engineering graphics drawing board with mini drafter, set squares and clips.', 'drafting, drawing, graphics, board', 2, 'Validated');

-- ============================================================
-- Quick verification queries (optional)
-- ============================================================
-- SELECT COUNT(*) AS total_listings FROM listings;
-- SELECT student_id, name, email, wallet_balance FROM students;
