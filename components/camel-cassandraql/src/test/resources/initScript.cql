CREATE KEYSPACE IF NOT EXISTS camel_ks WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

CREATE TABLE camel_ks.camel_user (
  login varchar PRIMARY KEY,
  first_name varchar,
  last_name varchar
);

CREATE TABLE camel_ks.NAMED_CAMEL_AGGREGATION (
  NAME varchar,
  KEY varchar,
  EXCHANGE_ID varchar,
  EXCHANGE blob,
  PRIMARY KEY (NAME, KEY)
);

CREATE TABLE camel_ks.NAMED_CAMEL_IDEMPOTENT (
  NAME varchar,
  KEY varchar,
  PRIMARY KEY (NAME, KEY)
);

CREATE TABLE camel_ks.CAMEL_IDEMPOTENT (
  KEY varchar,
  PRIMARY KEY (KEY)
);

CREATE TABLE camel_ks.CAMEL_AGGREGATION (
  KEY varchar,
  EXCHANGE_ID varchar,
  EXCHANGE blob,
  PRIMARY KEY (KEY)
);
