-- 原生选择远程回答：独立增量迁移，不修改已有通知记录。
CREATE TABLE IF NOT EXISTS notification_question (
  record_id bigint NOT NULL,
  credential_id bigint NOT NULL,
  request_id varchar(128) COLLATE utf8mb4_bin NOT NULL,
  content_hash char(64) NOT NULL,
  questions_json mediumtext NOT NULL,
  answer_json mediumtext DEFAULT NULL,
  state varchar(16) NOT NULL DEFAULT 'WAITING',
  source varchar(16) DEFAULT NULL,
  instance_id varchar(128) NOT NULL,
  agent_id varchar(128) DEFAULT NULL,
  call_id varchar(128) DEFAULT NULL,
  create_time datetime NOT NULL,
  update_time datetime NOT NULL,
  PRIMARY KEY (record_id),
  UNIQUE KEY uk_question_request (credential_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知交互问题与最终答案';
