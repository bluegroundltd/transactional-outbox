BEGIN

CREATE TABLE IF NOT EXISTS outbox_item
(
    id             bigint,
    type           text                         NOT NULL,
    status         text                         NOT NULL,
    payload        text                         NOT NULL,
    group_id       text,
    retries        bigint                       NOT NULL,
    next_run       timestamp                    NOT NULL,
    last_execution timestamp,
    rerun_after    timestamp,
    delete_after   timestamp,
    version        numeric                      DEFAULT 0,
    created_at     timestamp without time zone  NOT NULL,
    created_by     numeric                      DEFAULT 0,
    updated_at     timestamp without time zone  NOT NULL,
    updated_by     numeric                      DEFAULT 0,

    CONSTRAINT outbox_item_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS outbox_item_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    OWNED BY outbox_item.id;

COMMENT ON TABLE outbox_item is 'Table that holds the outbox items for the transactional-outbox library';
COMMENT ON COLUMN outbox_item.id is 'Id of the outbox item';
COMMENT ON COLUMN outbox_item.type is 'Type of the outbox item';
COMMENT ON COLUMN outbox_item.status is 'Status of the outbox item';
COMMENT ON COLUMN outbox_item.payload is 'Payload of the outbox item';
COMMENT ON COLUMN outbox_item.group_id is 'Group id of the outbox item';
COMMENT ON COLUMN outbox_item.retries is 'Current retries of the outbox item';
COMMENT ON COLUMN outbox_item.next_run is 'Timestamp of when to retry execution';
COMMENT ON COLUMN outbox_item.last_execution is 'Timestamp of the last execution time of the outbox item';
COMMENT ON COLUMN outbox_item.rerun_after is 'Timestamp of when to rerun after failure';
COMMENT ON COLUMN outbox_item.delete_after IS 'The timestamp after which the outbox item should be deleted';
COMMENT ON COLUMN outbox_item.version is 'Optimistic lock version field.';
COMMENT ON COLUMN outbox_item.created_at is 'Timestamp of when this item was created';
COMMENT ON COLUMN outbox_item.created_by is 'Id of the user who created this item';
COMMENT ON COLUMN outbox_item.updated_at is 'Timestamp of when this item was updated';
COMMENT ON COLUMN outbox_item.updated_by is 'Id of the user who updated this item';

CREATE INDEX IF NOT EXISTS idx_outbox_item_status ON outbox_item (status);
CREATE INDEX IF NOT EXISTS idx_outbox_item_next_run ON outbox_item (next_run);
CREATE INDEX IF NOT EXISTS idx_outbox_item_rerun_after ON outbox_item (rerun_after);
CREATE INDEX IF NOT EXISTS idx_outbox_item_delete_after ON outbox_item (delete_after);

-- Creating HASH index on group_id column since we are only interested in equality checks.
-- It is also created as a partial index since we expect the majority of the values to be NULL.
-- https://www.postgresql.org/docs/current/indexes-partial.html
CREATE INDEX IF NOT EXISTS outbox_item_group_id_idx ON outbox_item USING HASH (group_id)
    WHERE group_id IS NOT NULL;

COMMIT;
