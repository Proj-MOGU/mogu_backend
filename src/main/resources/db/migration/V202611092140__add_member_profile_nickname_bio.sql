ALTER TABLE member_profile ADD COLUMN nickname VARCHAR(12);
ALTER TABLE member_profile ADD COLUMN nickname_key VARCHAR(12);
ALTER TABLE member_profile ADD COLUMN bio VARCHAR(50);
ALTER TABLE member_profile ADD COLUMN nickname_changed_at TIMESTAMPTZ;
ALTER TABLE member_profile ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE member_profile ADD CONSTRAINT uk_member_profile_nickname_key UNIQUE (nickname_key);
ALTER TABLE member_profile ADD CONSTRAINT ck_member_profile_nickname_pair
    CHECK ((nickname IS NULL) = (nickname_key IS NULL));
