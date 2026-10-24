CREATE TABLE agenda (
    id smallint PRIMARY KEY CHECK (id = 1)
);
INSERT INTO agenda (id) VALUES (1);

CREATE TABLE call_sessions (
    id varchar(128) PRIMARY KEY CHECK (btrim(id) <> ''),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED', 'EXPIRED')),
    last_activity_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > last_activity_at)
);
CREATE INDEX call_sessions_expiry ON call_sessions (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE appointments (
    id uuid PRIMARY KEY,
    call_id varchar(128) NOT NULL REFERENCES call_sessions (id),
    date_time timestamp without time zone NOT NULL,
    customer_name text,
    status varchar(16) NOT NULL CHECK (status IN ('TEMPORARY', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (EXTRACT(MINUTE FROM date_time) IN (0, 30) AND EXTRACT(SECOND FROM date_time) = 0),
    CHECK (customer_name IS NULL OR btrim(customer_name) <> ''),
    CHECK (status <> 'CONFIRMED' OR customer_name IS NOT NULL)
);
CREATE UNIQUE INDEX appointments_occupied_time ON appointments (date_time)
    WHERE status IN ('TEMPORARY', 'CONFIRMED');
CREATE UNIQUE INDEX appointments_one_temporary_per_call ON appointments (call_id)
    WHERE status = 'TEMPORARY';
CREATE INDEX appointments_call_id ON appointments (call_id);
