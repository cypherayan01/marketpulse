-- Choices made on the dashboard (e.g. the BTST price range), one row per setting.
CREATE TABLE app_setting (
    name          VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(200) NOT NULL
);
