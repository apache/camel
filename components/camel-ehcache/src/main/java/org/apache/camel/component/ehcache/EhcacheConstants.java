/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.ehcache;


public interface EhcacheConstants {
    String ACTION = "CamelEhcacheAction";
    String ACTION_HAS_RESULT = "CamelEhcacheActionHasResult";
    String ACTION_SUCCEEDED = "CamelEhcacheActionSucceeded";
    String KEY = "CamelEhcacheKey";
    String KEYS = "CamelEhcacheKeys";
    String VALUE = "CamelEhcacheValue";
    String OLD_VALUE = "CamelEhcacheOldValue";
    String EVENT_TYPE = "CamelEhcacheEventType";

    String ACTION_CLEAR = "CLEAR";
    String ACTION_PUT = "PUT";
    String ACTION_PUT_ALL = "PUT_ALL";
    String ACTION_PUT_IF_ABSENT = "PUT_IF_ABSENT";
    String ACTION_GET = "GET";
    String ACTION_GET_ALL = "GET_ALL";
    String ACTION_REMOVE = "REMOVE";
    String ACTION_REMOVE_ALL = "REMOVE_ALL";
    String ACTION_REPLACE = "REPLACE";
}
