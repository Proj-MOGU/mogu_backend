ALTER TABLE auth_account ADD COLUMN name_enc TEXT;
ALTER TABLE auth_account ADD COLUMN phone_enc TEXT;
ALTER TABLE auth_social_identity ADD COLUMN email_enc TEXT;
ALTER TABLE auth_social_identity ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_mock_identity_verification ADD COLUMN phone_enc TEXT;
ALTER TABLE auth_mock_identity_verification ADD COLUMN name_enc TEXT;
