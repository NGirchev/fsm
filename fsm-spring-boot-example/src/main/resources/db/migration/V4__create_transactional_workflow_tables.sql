CREATE TABLE external_workflow (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL,
    state VARCHAR(255) NOT NULL,
    external_result VARCHAR(255),
    notification_sent BOOLEAN NOT NULL
);

CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    rev INTEGER PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE external_workflow_aud (
    rev INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    id BIGINT NOT NULL,
    version BIGINT,
    state VARCHAR(255),
    external_result VARCHAR(255),
    notification_sent BOOLEAN,
    PRIMARY KEY (rev, id)
);
