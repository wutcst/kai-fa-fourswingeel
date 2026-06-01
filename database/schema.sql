CREATE DATABASE IF NOT EXISTS cardgame;
USE cardgame;

-- 1. 用户表
CREATE TABLE `users` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `last_login` DATETIME
);

-- 2. 角色/职业表（初始牌组等）
CREATE TABLE `roles` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `role_name` VARCHAR(50) NOT NULL UNIQUE,
    `max_hp` INT NOT NULL,
    `starting_deck` TEXT   -- 存牌组ID列表，如 "[1,2,3]"
);

-- 3. 卡牌表
CREATE TABLE `cards` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `card_name` VARCHAR(100) NOT NULL,
    `card_type` VARCHAR(20) NOT NULL,  -- ATTACK, SKILL, POWER
    `cost` INT NOT NULL,
    `effect_desc` TEXT,
    `effect_code` VARCHAR(50)          -- 用于代码匹配，如 "STRIKE"
);

-- 4. 牌组表（用户拥有的卡牌及数量）
CREATE TABLE `decks` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `card_id` INT NOT NULL,
    `quantity` INT DEFAULT 1,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
    FOREIGN KEY (`card_id`) REFERENCES `cards`(`id`),
    INDEX (`user_id`)
);

-- 5. 怪物表
CREATE TABLE `monsters` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `monster_name` VARCHAR(50) NOT NULL,
    `hp` INT NOT NULL,
    `attack` INT NOT NULL,
    `ai_script` VARCHAR(100)   -- 存储策略类名或脚本路径
);

-- 6. 遗物表
CREATE TABLE `relics` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `relic_name` VARCHAR(100) NOT NULL,
    `effect_desc` TEXT,
    `effect_code` VARCHAR(50)
);

-- 7. 战斗记录表
CREATE TABLE `battle_records` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `floor_number` INT NOT NULL,
    `result` VARCHAR(20),   -- WIN, LOSE, ESCAPE
    `timestamp` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
    INDEX (`user_id`, `timestamp`)
);

-- 8. 操作日志表
CREATE TABLE `operation_logs` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT,
    `operation_type` VARCHAR(50) NOT NULL,
    `details` TEXT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX (`user_id`, `create_time`)
);

-- 可选：插入一条测试用户
INSERT INTO `users` (`username`, `password_hash`) VALUES ('test', 'dummy_hash');