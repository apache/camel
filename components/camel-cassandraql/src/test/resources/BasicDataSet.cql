CREATE TABLE camel_user (
  login varchar PRIMARY KEY,
  first_name varchar,
  last_name varchar
);
INSERT INTO camel_user(login, first_name, last_name)
    VALUES('j_strachan','James','Strachan');
INSERT INTO camel_user(login, first_name, last_name)
    VALUES('c_ibsen','Claus','Ibsen');
