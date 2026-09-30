-- 토큰 무효화: 로그아웃하거나 비밀번호를 바꾸면 이 값을 올려, 그 전에 발급한 토큰을 모두 거부한다.
ALTER TABLE users ADD COLUMN token_version INTEGER DEFAULT 0 NOT NULL;
