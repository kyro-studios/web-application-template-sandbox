-- お知らせの一覧 (アーキテクチャ設計 5.2、5.4)。
-- 公開日時は、元の表記 (published_at_text) を CHECK で形式に限り、timestamptz (published_at) を生成列で導く。

CREATE FUNCTION announcement_published_at(value text) RETURNS timestamptz
    LANGUAGE sql IMMUTABLE AS $$ SELECT value::timestamptz $$;

CREATE TABLE announcements (
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             text        NOT NULL,
    body              text        NOT NULL,
    published_at_text text        NOT NULL,
    published_at      timestamptz GENERATED ALWAYS AS (announcement_published_at(published_at_text)) STORED,
    CONSTRAINT announcements_published_at_text_format CHECK (
        published_at_text ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\.[0-9]{1,6})?)?(Z|[+-][0-9]{2}:[0-9]{2})$'
    )
);

CREATE INDEX announcements_published_order_idx
    ON announcements (published_at DESC, id DESC);

GRANT SELECT ON announcements TO ${app_db_user};
