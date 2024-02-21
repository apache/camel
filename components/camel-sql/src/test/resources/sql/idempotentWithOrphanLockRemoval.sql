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

-- Add DDL to create tables, views, indexes, etc needed by tests. These should match the expected database structure as it will appear in production.
SET DATABASE SQL SYNTAX PGS TRUE; -- tells HSQLDB that this schema uses MYSQL syntax
SET PROPERTY "sql.enforce_strict_size" FALSE;

CREATE TABLE CAMEL_MESSAGEPROCESSED (processorName VARCHAR(255), messageId VARCHAR(100), createdAt TIMESTAMP);

ALTER TABLE CAMEL_MESSAGEPROCESSED ADD PRIMARY KEY (processorName, messageId);


INSERT INTO CAMEL_MESSAGEPROCESSED VALUES ('APP_1', 'FILE_1', CURRENT_TIMESTAMP);

INSERT INTO CAMEL_MESSAGEPROCESSED VALUES ('APP_1', 'FILE_2',TIMESTAMPADD(SQL_TSI_MINUTE, -2, CURRENT_TIMESTAMP));

INSERT INTO CAMEL_MESSAGEPROCESSED VALUES ('APP_1', 'FILE_3',TIMESTAMPADD(SQL_TSI_MINUTE, -5, CURRENT_TIMESTAMP));
