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

create table customer (id varchar(15), name varchar(10));
insert into customer values('cust1','jstrachan');
insert into customer values('cust2','nsandhu');
insert into customer values('cust3','willem');

create table tableWithAutoIncr (id int not null GENERATED ALWAYS AS IDENTITY, content varchar(10));
insert into tableWithAutoIncr (content) values ('value1');

create table tableWithClob (id varchar(15), picture clob(10M));
insert into tableWithClob values ('id1', cast('\x0123456789ABCDEF' as clob));