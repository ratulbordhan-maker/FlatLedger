-- ============================================================================
--  WARNING: THIS DELETES EVERYTHING IN YOUR LOCAL flatmeals DATABASE.
--  Use it only on your own laptop, to throw away test data.
--  NEVER run it on the live server.
-- ============================================================================
--
-- How to use (MySQL Workbench, connected as root):
--   1. Stop the backend (Ctrl+C in its terminal).
--   2. File > Open SQL Script > this file, then click the lightning bolt.
--   3. Start the backend again (mvn spring-boot:run). Flyway rebuilds every
--      table, and the first-manager login is created again (manager / changeme123
--      unless you set MANAGER_* variables).

DROP DATABASE IF EXISTS flatmeals;
CREATE DATABASE flatmeals CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON flatmeals.* TO 'flatmeals'@'localhost';
FLUSH PRIVILEGES;
