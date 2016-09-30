-- Type of a catalog
-- a classic reference table
CREATE TABLE catalog_type (
  id VARCHAR(16) PRIMARY KEY
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
('taktilesbuch');
