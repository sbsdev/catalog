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

CREATE TABLE producer_mapping (
  id SMALLINT PRIMARY KEY,
  name VARCHAR(256)
);

DROP TABLE producer_mapping;

INSERT INTO producer_mapping (id, name) VALUES
(1, "Blista, Marburg"),
(2, "BBH, Berlin"),
(4, "NBH, Hamburg"),
(5, "BBH, München"),
(6, "WBH, Münster"),
(7, "Blindenhörbücherei des Saarlandes"),
(8, "SBH, Stuttgart"),
(9, "EBS, Marburg"),
(10, "CFB, Hamburg"),
(11, "DKBB, Bonn"),
(12, "Hörbücherei der Stimme der Hoffnung"),
(13, "SBS, Zürich"),
(24, "DZB, Leipzig"),
(25, "CAB, Landschlacht"),
(26, "Reformierte Blindenpflege Zürich"),
(27, "HSL, Kreuzlingen"),
(30, "VzFB, Hannover"),
(31, "BBI, Wien"),
(32, "Paderborn"),
(33, "Zollikofen"),
(34, "CBD, Wernigerode"),
(40, "BIT, München"),
(50, "BSVÖ, Wien"),
(103, "Planer-Regis, Berkenthin"),
(109, "ONCE, Madrid"),
(112, "Freunde blinder und sehbehinderter Kinder, Hamburg"),
(113, "The Princeton Braillists, Princeton"),
(115, "Nota, Kopenhagen"),
(117, "VA, Melbourne"),
(118, "SALB, Grahamstown"),
(119, "Visability, Victoria Park"),
(121, "Bartkowski, München"),
(123, "Braille-Kinderbücher, Düren"),
(124, "Berlin, Anderes Sehen"),
(128, "IKS, Kassel"),
(140, "Velen Integrationsspiele, Neuwied"),
(153, "Vogel, Hamburg"),
(154, "Alexander Reuss, Schwetzingen/Strassburg"),
(159, "Blindenunterrichtsanstalt, Frankfurt"),
(242, "SZB, St. Gallen"),
(248, "B. Lang, Freiburt im Üechtland"),
(250, "Blindenunterichtsanstalt, Ilzach"),
(305, "RNIB, London"),
(350, "Biblioteca Italiana per Ciechi, Monza"),
(401, "American Braille Press, Paris"),
(402, "American Foundation for Overseas Blind, Paris"),
(404, "Association pour nos Aveugles, Paris"),
(405, "AVH, Paris"),
(407, "Institut Nationale des Jounes Aveugles, Paris"),
(490, "Stamperia Nazionale Braille, Florenz"),
(508, "American Printing House for the Blind, Louisville");
