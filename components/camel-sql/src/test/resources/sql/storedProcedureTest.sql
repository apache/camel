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

-- H2 stored procedures that return ResultSet instead of using OUT parameters
-- These reference Java methods that have been adapted for H2

CREATE ALIAS SUBNUMBERS FOR 'org.apache.camel.component.sql.stored.H2StoredProcedures.subnumbers';
CREATE ALIAS NILADIC FOR 'org.apache.camel.component.sql.stored.H2StoredProcedures.niladic';
CREATE ALIAS BATCHFN FOR 'org.apache.camel.component.sql.stored.H2StoredProcedures.batchfn';
CREATE ALIAS INOUTDEMO FOR 'org.apache.camel.component.sql.stored.H2StoredProcedures.inoutdemo';
CREATE ALIAS SUBNUMBERS_FUNCTION FOR 'org.apache.camel.component.sql.stored.H2StoredProcedures.subnumbersFunction';
