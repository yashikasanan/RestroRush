-- ===================================================================
-- RestroRush: Order History Database Schema
-- ===================================================================
-- Run this script in MySQL if you wish to initialize the database manually:
--   mysql -u root -p < schema.sql
-- (Note: The application will also automatically create the database and
--  table on startup if your MySQL user has CREATE privileges).
-- ===================================================================

CREATE DATABASE IF NOT EXISTS restrorush_db;
USE restrorush_db;

CREATE TABLE IF NOT EXISTS order_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    order_type VARCHAR(20) NOT NULL DEFAULT 'Dine-In',
    total_bill DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    items_summary TEXT NOT NULL,
    chef_name VARCHAR(50),
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
