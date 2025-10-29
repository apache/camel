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

SELECT * FROM UNNEST([
  STRUCT(
    1 AS seq,
    'Row 1' AS name,
    STRUCT('1 Main St' AS street, 'City1' AS city, '90001' AS zip) AS address,
    ['tag1', 'test'] AS tags,
    [STRUCT('email' AS type, 'user1ATexample.com' AS value), STRUCT('phone' AS type, '+1-555-0001' AS value)] AS contacts
  ),
  STRUCT(
    2 AS seq,
    'Row 2' AS name,
    STRUCT('2 Main St' AS street, 'City2' AS city, '90002' AS zip) AS address,
    ['tag2', 'test'] AS tags,
    [STRUCT('email' AS type, 'user2ATexample.com' AS value), STRUCT('phone' AS type, '+1-555-0002' AS value)] AS contacts
  )
]) ORDER BY seq
