CREATE TABLE IF NOT EXISTS judge_outbox (
    submission_id INT PRIMARY KEY,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME NULL,
    INDEX idx_judge_outbox_pending (published_at, submission_id),
    CONSTRAINT fk_judge_outbox_submission FOREIGN KEY (submission_id)
        REFERENCES submissions(submission_id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT IGNORE INTO judge_outbox (submission_id)
SELECT submission_id FROM submissions WHERE status IN ('Pending', 'JUDGING');
