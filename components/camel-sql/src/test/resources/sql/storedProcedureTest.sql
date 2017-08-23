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

CREATE PROCEDURE SUBNUMBERS(VALUE1 INTEGER, VALUE2 INTEGER,OUT RESULT INTEGER)
 PARAMETER STYLE JAVA
 LANGUAGE JAVA
 EXTERNAL NAME
'org.apache.camel.component.sql.stored.TestStoredProcedure.subnumbers';

CREATE PROCEDURE NILADIC()
 PARAMETER STYLE JAVA
 LANGUAGE JAVA
 EXTERNAL NAME
'org.apache.camel.component.sql.stored.TestStoredProcedure.niladic';

CREATE PROCEDURE BATCHFN(VALUE1 CHAR(10))
 PARAMETER STYLE JAVA
 LANGUAGE JAVA
 EXTERNAL NAME
'org.apache.camel.component.sql.stored.TestStoredProcedure.batchfn';

CREATE FUNCTION SUBNUMBERS_FUNCTION(VALUE1 INTEGER, VALUE2 INTEGER)
 RETURNS INTEGER
 PARAMETER STYLE JAVA
 LANGUAGE JAVA
 EXTERNAL NAME
'org.apache.camel.component.sql.stored.TestStoredFunction.subnumbers';
