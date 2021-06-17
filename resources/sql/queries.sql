
-- :name catalog :? :*
-- :doc Get the catalog for given `year` and `issue`
SELECT items
FROM catalogs
WHERE issue = :issue AND year = :year

-- :name editorial :? :*
-- :doc Get the editorial for given `year`, `issue` and `catalog_type`
SELECT content
FROM editorials
WHERE year = :year
AND issue = :issue
AND catalog_type = :catalog_type

-- :name recommendation :? :*
-- :doc Get the recommendation for given `year`, `issue` and `catalog_type`
SELECT content
FROM recommendations
WHERE year = :year
AND issue = :issue 
AND catalog_type = :catalog_type

-- :name save-catalog-internal! :! :n
-- :doc Insert or update the given `items` to the catalog for given `year` and `issue`
INSERT INTO catalogs (year, issue, items)
VALUES (:year, :issue, :items)
ON DUPLICATE KEY UPDATE
items = values(items);

-- :name save-editorial-internal! :! :n
-- :doc Insert or update the given `content` to editorials for given `year`, `issue` and `catalog_type`
INSERT INTO editorials (year, issue, catalog_type, content)
VALUES (:year, :issue, :catalog_type, :content)
ON DUPLICATE KEY UPDATE
content = values(content);

-- :name save-recommendation-internal! :! :n
-- :doc Insert or update the given `content` to recommendations for given `year`, `issue` and `catalog_type`
INSERT INTO recommendations (year, issue, catalog_type, content)
VALUES (:year, :issue, :catalog_type, :content)
ON DUPLICATE KEY UPDATE
content = values(content);

-- :name full-catalog :? :*
-- :doc Get the full catalog for given `year` and `catalog_type`
SELECT items
FROM full_catalogs
WHERE catalog_type = :catalog_type AND year = :year

-- :name save-full-catalog-internal! :! :n
-- :doc Insert or update the given `items` to the full_catalog for given `year` and `catalog_type`
INSERT INTO full_catalogs (year, catalog_type, items)
VALUES (:year, :catalog_type, :items)
ON DUPLICATE KEY UPDATE
items = values(items);

