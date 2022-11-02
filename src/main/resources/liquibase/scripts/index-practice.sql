-- liquibase formatted sql

-- changeSet Shadrin:1
CREATE TABLE Notification_task
(
    id                BIGSERIAL PRIMARY KEY,
    id_chat           BIGSERIAL,
    notification_text TEXT,
    date_Time         TIMESTAMP
);
