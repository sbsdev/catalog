-- Type of a catalog
-- a classic reference table
CREATE TABLE catalog_type (
  id VARCHAR(16) PRIMARY KEY
);
--;;

CREATE TABLE catalogs (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  year YEAR,
  issue TINYINT,
  items MEDIUMTEXT NOT NULL,
  UNIQUE(year, issue)
);
--;;

CREATE TABLE full_catalogs (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  year YEAR,
  catalog_type VARCHAR(16) NOT NULL,
  items MEDIUMTEXT NOT NULL,
  UNIQUE(year, catalog_type)
);
--;;

CREATE TABLE recommendations (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  content TEXT NOT NULL,
  catalog_type VARCHAR(16) NOT NULL,
  issue_id INTEGER NOT NULL,
  FOREIGN KEY(issue_id) REFERENCES catalogs(id),
  FOREIGN KEY(catalog_type) REFERENCES catalog_type(id),
  UNIQUE(issue_id,catalog_type)
);
--;;

CREATE TABLE editorials (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  content TEXT NOT NULL,
  catalog_type VARCHAR(16) NOT NULL,
  issue_id INTEGER NOT NULL,
  FOREIGN KEY(issue_id) REFERENCES catalogs(id),
  FOREIGN KEY(catalog_type) REFERENCES catalog_type(id),
  UNIQUE(issue_id,catalog_type)
);
--;;

INSERT INTO catalog_type VALUES
('sortiment'),
('grossdruck'),
('braille'),
('hörbuch'),
('hörfilm'),
('ludo'),
('taktil');
