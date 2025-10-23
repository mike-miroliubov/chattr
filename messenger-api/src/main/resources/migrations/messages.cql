CREATE KEYSPACE IF NOT EXISTS chattr
WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor': 1 };

/*
Need to add another part to partition key to reduce partition size.
It could be a date but then it could be tricky to find the previous partitions - no guarantee that people chatted yesterday,
the previous month or even year.

Ideally it should be a sequential number. We could call it the epoch. The challenge is how to make 2 (or more) client actors agree on it.
Probably its fine if they dont as long as eventually they will.

For group chats the epoch could be controlled by the GroupExchange and communicated back to the clients.
This means though that the save operation should happen in the GroupExchange though. This will delay the first acknowledgement.

For private chats, the epoch could be controlled by both of the private Exchanges and echoed back if the epoch changed.

How do we know if we need to increase the epoch? We count messages, new epoch every 1000 messages or 10000 messages.
How do we count the messages?
*/
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