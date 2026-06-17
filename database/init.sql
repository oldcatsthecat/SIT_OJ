DROP DATABASE IF EXISTS sit_oj;

CREATE DATABASE sit_oj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sit_oj;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
                                     id INT PRIMARY KEY AUTO_INCREMENT,
                                     username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) DEFAULT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    gender VARCHAR(10),
    real_name VARCHAR(50),
    student_id VARCHAR(20),
    email VARCHAR(100) UNIQUE,
    email_code VARCHAR(10),
    code_expire_time DATETIME,
    age INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
    ) ENGINE=InnoDB;

-- 2. 比赛表
CREATE TABLE IF NOT EXISTS competitions (
                                            competition_id INT PRIMARY KEY AUTO_INCREMENT,
                                            competition_name VARCHAR(100) NOT NULL,
    start_time DATETIME,
    end_time DATETIME,
    freeze_minute INT DEFAULT 0 COMMENT '封榜时长(分钟)，0=不封榜，表示结束前N分钟冻结排名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;

-- 3. 题目表
CREATE TABLE IF NOT EXISTS problems (
                                        problem_id INT PRIMARY KEY AUTO_INCREMENT,
                                        problem_name VARCHAR(100) NOT NULL,
    problem_description TEXT,
    input_description TEXT,
    output_description TEXT,
    samples TEXT,
    hint TEXT,
    time_limit INT DEFAULT 1000,
    memory_limit INT DEFAULT 128,
    judge_type TINYINT DEFAULT 0,
    spj_code TEXT,
    difficulty VARCHAR(20) DEFAULT 'Low',
    is_public BOOLEAN DEFAULT FALSE ,
    problem_source VARCHAR(100),
    accepted_number INT DEFAULT 0,
    submission_number INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;

-- 4. 提交记录表
CREATE TABLE IF NOT EXISTS submissions (
                                           submission_id INT PRIMARY KEY AUTO_INCREMENT,
                                           user_id INT NOT NULL,
                                           problem_id INT NOT NULL,
                                           competition_id INT NULL,
                                           code_content TEXT NOT NULL,
                                           language VARCHAR(20) NOT NULL,
    status TEXT ,
    time_cost INT DEFAULT 0,
    memory_cost INT DEFAULT 0,
    judge_info TEXT,
    error_message TEXT,
    submission_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_problem_id (problem_id),
    CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_problem FOREIGN KEY (problem_id) REFERENCES problems(problem_id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_contest FOREIGN KEY (competition_id) REFERENCES competitions(competition_id) ON DELETE CASCADE
    ) ENGINE=InnoDB;

-- 5. 比赛题目关联表
CREATE TABLE IF NOT EXISTS competition_problems (
                                                    competition_id INT NOT NULL,
                                                    problem_id INT NOT NULL,
                                                    PRIMARY KEY (competition_id, problem_id),
    CONSTRAINT fk_cp_contest FOREIGN KEY (competition_id) REFERENCES competitions(competition_id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_problem FOREIGN KEY (problem_id) REFERENCES problems(problem_id) ON DELETE CASCADE
    ) ENGINE=InnoDB;

-- 6. 参赛表
CREATE TABLE IF NOT EXISTS participations (
                                              user_id INT NOT NULL,
                                              competition_id INT NOT NULL,
                                              solved_count INT DEFAULT 0,
                                              total_penalty INT DEFAULT 0,
                                              PRIMARY KEY (user_id, competition_id),
    CONSTRAINT fk_part_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_part_contest FOREIGN KEY (competition_id) REFERENCES competitions(competition_id) ON DELETE CASCADE
    ) ENGINE=InnoDB;

-- 7. 比赛提交统计表
CREATE TABLE IF NOT EXISTS competition_submission_stats (
                                                            user_id INT NOT NULL,
                                                            competition_id INT NOT NULL,
                                                            problem_id INT NOT NULL,
                                                            is_ac BOOLEAN DEFAULT FALSE,
                                                            wrong_attempts INT DEFAULT 0,
                                                            ac_time INT DEFAULT NULL,
                                                            PRIMARY KEY (user_id, competition_id, problem_id),
    CONSTRAINT fk_stats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_stats_contest FOREIGN KEY (competition_id) REFERENCES competitions(competition_id) ON DELETE CASCADE,
    CONSTRAINT fk_stats_problem FOREIGN KEY (problem_id) REFERENCES problems(problem_id) ON DELETE CASCADE
    ) ENGINE=InnoDB;


CREATE TRIGGER trg_after_delete_submission
    AFTER DELETE ON submissions
    FOR EACH ROW
BEGIN
    -- 无论删除的是什么状态，submission_number 都要减 1
    UPDATE problems
    SET submission_number = GREATEST(submission_number - 1, 0)
    WHERE problem_id = OLD.problem_id;

    IF OLD.status = 'AC' THEN
    UPDATE problems
    SET accepted_number = GREATEST(accepted_number - 1, 0)
    WHERE problem_id = OLD.problem_id;
END IF;
END
