--
-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--      http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

--
-- H2 database schema for Quartz Scheduler
-- Based on the official Quartz distribution schema
-- Adapted from: https://github.com/quartz-scheduler/quartz
--
-- H2 Setup Notes:
-- * Use org.quartz.impl.jdbcjobstore.StdJDBCDelegate as the driver delegate
-- * MVCC mode is enabled by default in H2 2.x and provides row-level locking for clustering
--

create table qrtz_job_details (
sched_name varchar(120) not null,
job_name varchar(200) not null,
job_group varchar(200) not null,
description varchar(250),
job_class_name varchar(250) not null,
is_durable boolean not null,
is_nonconcurrent boolean not null,
is_update_data boolean not null,
requests_recovery boolean not null,
job_data blob,
primary key (sched_name,job_name,job_group)
);

create table qrtz_triggers(
sched_name varchar(120) not null,
trigger_name varchar(200) not null,
trigger_group varchar(200) not null,
job_name varchar(200) not null,
job_group varchar(200) not null,
description varchar(250),
next_fire_time bigint,
prev_fire_time bigint,
priority integer,
trigger_state varchar(16) not null,
trigger_type varchar(8) not null,
start_time bigint not null,
end_time bigint,
calendar_name varchar(200),
misfire_instr smallint,
job_data blob,
primary key (sched_name,trigger_name,trigger_group),
foreign key (sched_name,job_name,job_group) references qrtz_job_details(sched_name,job_name,job_group)
);

create table qrtz_simple_triggers(
sched_name varchar(120) not null,
trigger_name varchar(200) not null,
trigger_group varchar(200) not null,
repeat_count bigint not null,
repeat_interval bigint not null,
times_triggered bigint not null,
primary key (sched_name,trigger_name,trigger_group),
foreign key (sched_name,trigger_name,trigger_group) references qrtz_triggers(sched_name,trigger_name,trigger_group)
);

create table qrtz_cron_triggers(
sched_name varchar(120) not null,
trigger_name varchar(200) not null,
trigger_group varchar(200) not null,
cron_expression varchar(120) not null,
time_zone_id varchar(80),
primary key (sched_name,trigger_name,trigger_group),
foreign key (sched_name,trigger_name,trigger_group) references qrtz_triggers(sched_name,trigger_name,trigger_group)
);

create table qrtz_simprop_triggers (
sched_name varchar(120) not null,
trigger_name varchar(200) not null,
trigger_group varchar(200) not null,
str_prop_1 varchar(512),
str_prop_2 varchar(512),
str_prop_3 varchar(512),
int_prop_1 int,
int_prop_2 int,
long_prop_1 bigint,
long_prop_2 bigint,
dec_prop_1 numeric(13,4),
dec_prop_2 numeric(13,4),
bool_prop_1 boolean,
bool_prop_2 boolean,
primary key (sched_name,trigger_name,trigger_group),
foreign key (sched_name,trigger_name,trigger_group) references qrtz_triggers(sched_name,trigger_name,trigger_group)
);

create table qrtz_blob_triggers(
sched_name varchar(120) not null,
trigger_name varchar(200) not null,
trigger_group varchar(200) not null,
blob_data blob,
primary key (sched_name,trigger_name,trigger_group),
foreign key (sched_name,trigger_name,trigger_group) references qrtz_triggers(sched_name,trigger_name,trigger_group)
);

create table qrtz_calendars(
sched_name varchar(120) not null,
calendar_name varchar(200) not null,
calendar blob not null,
primary key (sched_name,calendar_name)
);

create table qrtz_paused_trigger_grps (
sched_name varchar(120) not null,
trigger_group varchar(200) not null,
primary key (sched_name,trigger_group)
);

create table qrtz_fired_triggers(
sched_name varchar(120) not null,
entry_id varchar(95) not null,
trigger_name varchar(200) not null,
trigger_group varchar(200) not null,
instance_name varchar(200) not null,
fired_time bigint not null,
sched_time bigint not null,
priority integer not null,
state varchar(16) not null,
job_name varchar(200),
job_group varchar(200),
is_nonconcurrent boolean,
requests_recovery boolean,
primary key (sched_name,entry_id)
);

create table qrtz_scheduler_state (
sched_name varchar(120) not null,
instance_name varchar(200) not null,
last_checkin_time bigint not null,
checkin_interval bigint not null,
primary key (sched_name,instance_name)
);

create table qrtz_locks (
sched_name varchar(120) not null,
lock_name varchar(40) not null,
primary key (sched_name,lock_name)
);
