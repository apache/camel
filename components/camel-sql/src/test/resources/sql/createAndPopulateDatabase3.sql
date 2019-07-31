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

-- START SNIPPET: e1
create table projects (id integer primary key GENERATED ALWAYS AS IDENTITY, project varchar(10), license varchar(5), description varchar(1000) default null);
insert into projects (project, license, description) values ('Camel', 'ASF', '');
insert into projects (project, license, description) values ('AMQ', 'ASF', '');
insert into projects (project, license, description) values ('Linux', 'XXX', '');
-- END SNIPPET: e1

-- START SNIPPET: e2
create table developers (id1 integer primary key GENERATED ALWAYS AS IDENTITY (START WITH 5), name varchar(20), position varchar(20), id2 integer GENERATED ALWAYS AS (id1+1));
-- END SNIPPET: e2