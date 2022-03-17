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
package org.apache.camel.component.caffeine;

import org.apache.camel.spi.Metadata;

public interface CaffeineConstants {

    String ACTION_CLEANUP = "CLEANUP";
    String ACTION_PUT = "PUT";
    String ACTION_PUT_ALL = "PUT_ALL";
    String ACTION_GET = "GET";
    String ACTION_GET_ALL = "GET_ALL";
    String ACTION_INVALIDATE = "INVALIDATE";
    String ACTION_INVALIDATE_ALL = "INVALIDATE_ALL";
    String ACTION_AS_MAP = "AS_MAP";

    @Metadata(description = "The action to execute.\n\nPossible values:\n\n* " + ACTION_CLEANUP + "\n* "
                            + ACTION_PUT + "\n* " + ACTION_PUT_ALL + "\n* " + ACTION_GET + "\n* " + ACTION_GET_ALL + "\n* "
                            + ACTION_INVALIDATE + "\n* " + ACTION_INVALIDATE_ALL + "\n* " + ACTION_AS_MAP,
              javaType = "String")
    String ACTION = "CamelCaffeineAction";
    @Metadata(description = "The flag indicating whether the action has a result or not.", javaType = "Boolean")
    String ACTION_HAS_RESULT = "CamelCaffeineActionHasResult";
    @Metadata(description = "The flag indicating whether the action was successful or not.", javaType = "Boolean")
    String ACTION_SUCCEEDED = "CamelCaffeineActionSucceeded";
    @Metadata(description = "The key for all actions on a single entry.")
    String KEY = "CamelCaffeineKey";
    @Metadata(description = "The keys to get (" + ACTION_GET_ALL + "), to invalidate (" + ACTION_INVALIDATE_ALL
                            + ") or existing (" + ACTION_AS_MAP + ") according to the action.",
              javaType = "Set")
    String KEYS = "CamelCaffeineKeys";
    @Metadata(description = "The value of key for all put actions (" + ACTION_PUT + " or " + ACTION_PUT_ALL + ").")
    String VALUE = "CamelCaffeineValue";
    @Metadata(description = "The old value returned according to the action.")
    String OLD_VALUE = "CamelCaffeineOldValue";

}
