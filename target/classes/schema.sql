-- Spring Security: users and authorities tables used by JdbcUserDetailsManager.
-- Column names must match exactly — Spring Security queries them by name.
CREATE TABLE IF NOT EXISTS users (
    username  VARCHAR(50)  NOT NULL PRIMARY KEY,
    password  VARCHAR(500) NOT NULL,
    enabled   BOOLEAN      NOT NULL
);

CREATE TABLE IF NOT EXISTS authorities (
    username  VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users (username)
);

CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_username ON authorities (username, authority);

-- Ingestion job tracking: records every async ingest request and its outcome.
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    source        VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
