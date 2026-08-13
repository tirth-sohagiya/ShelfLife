-- Runs automatically the first time the postgres container starts with an
-- empty data volume. Creates one database per service, preserving the
-- "each service owns its own database" boundary from the design log.
CREATE DATABASE donation_db;
CREATE DATABASE request_db;
CREATE DATABASE matching_db;
