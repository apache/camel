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

-- HSQLDB stored procedures using PARAMETER STYLE JAVA with OUT parameters
-- These reference Java methods from TestStoredProcedure class

CREATE PROCEDURE SUBNUMBERS(IN param1 INTEGER, IN param2 INTEGER, OUT param3 INTEGER)
LANGUAGE JAVA
PARAMETER STYLE JAVA
NO SQL
EXTERNAL NAME 'CLASSPATH:org.apache.camel.component.sql.stored.TestStoredProcedure.subnumbers';

CREATE PROCEDURE NILADIC()
LANGUAGE JAVA
PARAMETER STYLE JAVA
NO SQL
EXTERNAL NAME 'CLASSPATH:org.apache.camel.component.sql.stored.TestStoredProcedure.niladic';

CREATE PROCEDURE BATCHFN(IN param1 VARCHAR(100))
LANGUAGE JAVA
PARAMETER STYLE JAVA
NO SQL
EXTERNAL NAME 'CLASSPATH:org.apache.camel.component.sql.stored.TestStoredProcedure.batchfn';

CREATE PROCEDURE INOUTDEMO(IN param1 INTEGER, INOUT param2 INTEGER, OUT param3 INTEGER)
LANGUAGE JAVA
PARAMETER STYLE JAVA
NO SQL
EXTERNAL NAME 'CLASSPATH:org.apache.camel.component.sql.stored.TestStoredProcedure.inoutdemo';