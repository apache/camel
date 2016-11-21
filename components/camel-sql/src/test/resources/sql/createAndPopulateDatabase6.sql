-- ------------------------------------------------------------------------
-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- START SNIPPET: e1
create table projects (id integer primary key, project varchar(10), license varchar(5));
insert into projects values (1, 'Camel', 'ASF');
insert into projects values (2, 'Camel', 'XXX');
insert into projects values (3, 'Camel', 'YYY');
insert into projects values (4, 'Camel', 'ZZZ');
insert into projects values (5, 'AMQ', 'ASF');
insert into projects values (6, 'AMQ', 'XXX');
insert into projects values (7, 'AMQ', 'YYY');
insert into projects values (8, 'AMQ', 'ZZZ');
insert into projects values (9, 'Linux', 'XXX');
-- END SNIPPET: e1