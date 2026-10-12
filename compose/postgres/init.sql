-- 開発と試験の PostgreSQL の初期化 (認証機能の設計 7.6)。
-- 所有者 app_owner は POSTGRES_USER で作られる。ここでは、アプリケーション用の利用者 app_user を作る。
-- パスワードは開発と試験専用の値。本番の環境では、運用の担当者が別に用意する。

CREATE ROLE app_user LOGIN PASSWORD 'app_user_password';
GRANT CONNECT ON DATABASE app TO app_user;
GRANT USAGE ON SCHEMA public TO app_user;
