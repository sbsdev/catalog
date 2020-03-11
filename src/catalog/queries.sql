-- name: catalog
-- Get the catalog for given `year` and `issue`.
SELECT items
FROM catalogs
WHERE issue = :issue AND year = :year

-- name: editorial
-- Get the editorial for given `year`, `issue` and `catalog_type`.
SELECT content
FROM editorials
WHERE editorials.year = :year AND editorials.issue = :issue 
AND editorials.catalog_type = :catalog_type

-- name: recommendation
-- Get the recommendation for given `year`, `issue` and
-- `catalog_type`.
SELECT content
FROM recommendations, catalogs
WHERE recommendations.year = :year AND recommendations.issue = :issue 
AND recommendations.catalog_type = :catalog_type

-- name: save-catalog-internal!
-- Insert or update the given `items` to the catalog for given `year`
-- and `issue`.
INSERT INTO catalogs (year, issue, items)
VALUES (:year, :issue, :items)
ON DUPLICATE KEY UPDATE
items = values(items);

-- name: save-editorial-internal!
-- Insert or update the given `content` to editorials for given
-- `year`, `issue` and `catalog_type`.
INSERT INTO editorials (year, issue, catalog_type, content)
VALUES (:year, :issue, :catalog_type, :content)
ON DUPLICATE KEY UPDATE
content = values(content);

-- name: save-recommendation-internal!
-- Insert or update the given `content` to recommendations for given `year`,
-- `issue` and `catalog_type`.
INSERT INTO recommendations (year, issue, catalog_type, content)
VALUES (:year, :issue, :catalog_type, :content)
ON DUPLICATE KEY UPDATE
content = values(content);

-- name: full-catalog
-- Get the full catalog for given `year` and `catalog_type`.
SELECT items
FROM full_catalogs
WHERE catalog_type = :catalog_type AND year = :year

-- name: save-full-catalog-internal!
-- Insert or update the given `items` to the full_catalog for given
-- `year` and `catalog_type`.
INSERT INTO full_catalogs (year, catalog_type, items)
VALUES (:year, :catalog_type, :items)
ON DUPLICATE KEY UPDATE
items = values(items);

-- name: producer-mapping-raw
-- Get the mapping of producer id and full_name.
SELECT id, name
FROM producer_mapping;

