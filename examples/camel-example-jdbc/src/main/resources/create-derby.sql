/*
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
/*
STATUS : 'NEW', 'DONE'
*/
CREATE TABLE CAMEL_TEST ( 
  ID BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
  MSG VARCHAR(10240),
  STATUS CHAR(4) DEFAULT 'NEW',
  CREATE_TS TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
