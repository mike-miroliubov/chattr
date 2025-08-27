CREATE KEYSPACE IF NOT EXISTS chattr
WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor': 1 };

CREATE TABLE chattr.message (
  chat_id VARCHAR,
  message_id VARCHAR,
  from_user_id VARCHAR,
  message TEXT,
  sent_at TIMESTAMP,
  received_at TIMESTAMP,
  delivered_at TIMESTAMP,
  PRIMARY KEY (chat_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

CREATE TABLE chattr.inbox (
  user_id VARCHAR,
  chat_id VARCHAR,
  last_message_from_user_id VARCHAR,
  last_message_sent_at TIMESTAMP,
  last_message_id VARCHAR,
  last_message TEXT,
  new_messages INT,
  PRIMARY KEY (user_id, chat_id, last_message_id)
) WITH CLUSTERING ORDER BY (chat_id ASC, last_message_id DESC);