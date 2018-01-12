-- Type of a catalog
-- a classic reference table
CREATE TABLE catalog_type (
  id VARCHAR(25) PRIMARY KEY
);

CREATE TABLE catalogs (
  year YEAR NOT NULL,
  issue TINYINT NOT NULL,
  -- the items just contain edn of the data dump of the library data
  -- for that particular issue
  items MEDIUMTEXT NOT NULL,
  PRIMARY KEY(year, issue)
);

CREATE TABLE full_catalogs (
  year YEAR NOT NULL,
  catalog_type VARCHAR(16) NOT NULL,
  -- the items just contain edn of the data dump of the library data
  -- for that particular full catalog
  items MEDIUMTEXT NOT NULL,
  PRIMARY KEY(year, catalog_type),
  FOREIGN KEY(catalog_type) REFERENCES catalog_type(id)
);

CREATE TABLE recommendations (
  year YEAR NOT NULL,
  issue TINYINT NOT NULL,
  catalog_type VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  PRIMARY KEY(year, issue, catalog_type),
  FOREIGN KEY(year, issue) REFERENCES catalogs(year, issue),
  FOREIGN KEY(catalog_type) REFERENCES catalog_type(id)
);

CREATE TABLE editorials (
  year YEAR NOT NULL,
  issue TINYINT NOT NULL,
  catalog_type VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  PRIMARY KEY(year, issue, catalog_type),
  FOREIGN KEY(year, issue) REFERENCES catalogs(year, issue),
  FOREIGN KEY(catalog_type) REFERENCES catalog_type(id)
);

INSERT INTO catalog_type VALUES
('sortiment'),
('grossdruck'),
('braille'),
('hörbuch'),
('hörfilm'),
('ludo'),
('taktilesbuch')
('print-and-braille');

-- Update the catalog_type column of the full_catalogs table
SHOW CREATE TABLE full_catalogs;

ALTER TABLE catalog_type MODIFY id VARCHAR(25);
ALTER TABLE full_catalogs DROP FOREIGN KEY full_catalogs_ibfk_1;
ALTER TABLE full_catalogs MODIFY catalog_type VARCHAR(25), ADD FOREIGN KEY full_catalogs_ibfk_1 (catalog_type) REFERENCES catalog_type(id);

INSERT INTO catalog_type VALUES ('print-and-braille');
