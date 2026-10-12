-- お知らせの一覧の性能試験の準備 (NFR-3、アーキテクチャ設計 7.2)。
-- announcements を空にしてから、公開中 500 件と公開前 500 件を入れる。開発の環境のデータは消える。
-- 所有者 app_owner で実行する (app_user は SELECT だけを持つ)。手順は perf/README.md を参照。
\set ON_ERROR_STOP on

TRUNCATE announcements RESTART IDENTITY;

-- 公開中 500 件。公開日時は、表示の時点より 1 時間以上前。
-- i が 1 から 250 は時刻が重ならず、251 から 500 は 1 から 250 と同じ時刻になる (重なる値)。
-- 後半は +09:00 で書き、表し方が違っても同じ時点として並ぶことを確かめられるようにする。
INSERT INTO announcements (title, body, published_at_text)
SELECT
    '公開中のお知らせ ' || i,
    '性能試験用の本文 ' || i,
    CASE
        WHEN i <= 250 THEN to_char((now() - make_interval(hours => i)) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS') || 'Z'
        ELSE to_char((now() - make_interval(hours => i - 250)) AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD"T"HH24:MI:SS') || '+09:00'
    END
FROM generate_series(1, 500) AS i;

-- 公開前 500 件。公開日時は、表示の時点より 1 時間以上後。重なりの作り方は公開中と同じ。
INSERT INTO announcements (title, body, published_at_text)
SELECT
    '公開前のお知らせ ' || i,
    '性能試験用の本文 ' || i,
    CASE
        WHEN i <= 250 THEN to_char((now() + make_interval(hours => i)) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS') || 'Z'
        ELSE to_char((now() + make_interval(hours => i - 250)) AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD"T"HH24:MI:SS') || '+09:00'
    END
FROM generate_series(1, 500) AS i;
