-- 開発用の初期化 (認証機能の設計 7.6)
-- マイグレーション用の利用者 app_owner は、イメージが POSTGRES_USER として作る。
-- ここでは、アプリケーション用の利用者 app_user を作る。パスワードは compose.yaml の APP_DB_PASSWORD から読む。

\getenv app_db_password APP_DB_PASSWORD
CREATE ROLE app_user LOGIN PASSWORD :'app_db_password';
GRANT USAGE ON SCHEMA public TO app_user;
