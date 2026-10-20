-- Run once before deploying the competition service. Existing participants remain on the contest ranklist.
ALTER TABLE participations
    ADD COLUMN is_practice TINYINT(1) NOT NULL DEFAULT 0;
