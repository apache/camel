DROP TABLE aggregationRepo2 IF EXISTS;
DROP TABLE aggregationRepo2_completed IF EXISTS;
CREATE TABLE aggregationRepo2 (
    id varchar(255) NOT NULL,
    exchange blob NOT NULL,
    constraint aggregationRepo2_pk PRIMARY KEY (id)
);
CREATE TABLE aggregationRepo2_completed (
    id varchar(255) NOT NULL,
    exchange blob NOT NULL,
    constraint aggregationRepo2_completed_pk PRIMARY KEY (id)
);