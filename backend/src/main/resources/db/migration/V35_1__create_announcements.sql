-- お知らせの一覧 (お知らせの一覧の設計 5.2)
-- 公開日時は、登録された表記 (published_at_text) を正とする。時点として比べる published_at は、そこから導く生成列にする。

-- CHECK で、タイムゾーン (ずれ) を必ず含む値だけを保存する。そのため、この関数の結果はセッションのタイムゾーンに依らず、IMMUTABLE と宣言できる。
CREATE FUNCTION announcement_published_at(value text) RETURNS timestamptz
    LANGUAGE sql IMMUTABLE AS $$ SELECT value::timestamptz $$;

-- 運営が、SQL で直接登録する。長さの制約と UNIQUE は付けない (登録された値のまま表示する。同じ内容も別々に扱う)
CREATE TABLE announcements (
    -- 登録の順序。運営が値を指定できない (ALWAYS)
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             text        NOT NULL,
    body              text        NOT NULL,
    -- 公開日時の元の表記。NULL、ずれの無い値、空白区切り、infinity は拒否する
    published_at_text text        NOT NULL,
    published_at      timestamptz GENERATED ALWAYS AS (announcement_published_at(published_at_text)) STORED,
    CONSTRAINT announcements_published_at_text_format CHECK (
        published_at_text ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\.[0-9]{1,6})?)?(Z|[+-][0-9]{2}:[0-9]{2})$'
    )
);

CREATE INDEX announcements_published_order_idx
    ON announcements (published_at DESC, id DESC);

-- アプリケーション用の利用者は読み取りだけ (R-6)
GRANT SELECT ON announcements TO ${app_db_user};
