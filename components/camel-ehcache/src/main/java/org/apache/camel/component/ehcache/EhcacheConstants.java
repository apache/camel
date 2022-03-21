/*
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

import org.apache.camel.spi.Metadata;

public interface EhcacheConstants {
    @Metadata(description = "The operation to be performed on the cache, valid options are:\n" +
                            "\n" +
                            "* CLEAR\n" +
                            "* PUT\n" +
                            "* PUT_ALL\n" +
                            "* PUT_IF_ABSENT\n" +
                            "* GET\n" +
                            "* GET_ALL\n" +
                            "* REMOVE\n" +
                            "* REMOVE_ALL\n" +
                            "* REPLACE",
              javaType = "String")
    String ACTION = "CamelEhcacheAction";
    @Metadata(description = "Set to true if the action has a result", javaType = "Boolean")
    String ACTION_HAS_RESULT = "CamelEhcacheActionHasResult";
    @Metadata(description = "Set to true if the action was successful", javaType = "Boolean")
    String ACTION_SUCCEEDED = "CamelEhcacheActionSucceeded";
    @Metadata(description = "The cache key used for an action", javaType = "Object")
    String KEY = "CamelEhcacheKey";
    @Metadata(description = "A list of keys, used in\n" +
                            "\n" +
                            "* PUT_ALL\n" +
                            "* GET_ALL\n" +
                            "* REMOVE_ALL\n",
              javaType = "Set<Object>")
    String KEYS = "CamelEhcacheKeys";
    @Metadata(description = "The value to put in the cache or the result of an operation", javaType = "Object")
    String VALUE = "CamelEhcacheValue";
    @Metadata(description = "The old value associated to a key for actions like PUT_IF_ABSENT or the\n" +
                            "Object used for comparison for actions like REPLACE",
              javaType = "Object")
    String OLD_VALUE = "CamelEhcacheOldValue";
    @Metadata(description = "The type of event received", javaType = "EventType")
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
