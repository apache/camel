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
package org.apache.camel.component.caffeine;


public interface CaffeineConstants {
    String ACTION = "CamelCaffeineAction";
    String ACTION_HAS_RESULT = "CamelCaffeineActionHasResult";
    String ACTION_SUCCEEDED = "CamelCaffeineActionSucceeded";
    String KEY = "CamelCaffeineKey";
    String KEYS = "CamelCaffeineKeys";
    String VALUE = "CamelCaffeineValue";
    String OLD_VALUE = "CamelCaffeineOldValue";

    String ACTION_CLEANUP = "CLEANUP";
    String ACTION_PUT = "PUT";
    String ACTION_PUT_ALL = "PUT_ALL";
    String ACTION_GET = "GET";
    String ACTION_GET_ALL = "GET_ALL";
    String ACTION_INVALIDATE = "INVALIDATE";
    String ACTION_INVALIDATE_ALL = "INVALIDATE_ALL";
}
