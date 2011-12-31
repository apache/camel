CREATE TABLE aggregationRepo1 (
    id varchar(255) NOT NULL,
    exchange blob NOT NULL,
    constraint aggregationRepo1_pk PRIMARY KEY (id)
);
CREATE TABLE aggregationRepo1_completed (
    id varchar(255) NOT NULL,
    exchange blob NOT NULL,
    constraint aggregationRepo1_completed_pk PRIMARY KEY (id)
);