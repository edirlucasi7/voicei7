CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE vector_store (
    id uuid PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);
CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE TABLE knowledge_source (
    path varchar(1024) PRIMARY KEY,
    content_hash varchar(64) NOT NULL
);
CREATE TABLE knowledge_catalog (
    id smallint PRIMARY KEY CHECK (id = 1)
);
INSERT INTO knowledge_catalog (id) VALUES (1);
