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
package org.apache.camel.component.infinispan;

public interface InfinispanConstants {

    String CACHE_MANAGER_CURRENT = "current";

    String EVENT_TYPE = "CamelInfinispanEventType";
    String IS_PRE = "CamelInfinispanIsPre";
    String CACHE_NAME = "CamelInfinispanCacheName";
    String KEY = "CamelInfinispanKey";
    String VALUE = "CamelInfinispanValue";
    String DEFAULT_VALUE = "CamelInfinispanDefaultValue";
    String OLD_VALUE = "CamelInfinispanOldValue";
    String MAP = "CamelInfinispanMap";
    String OPERATION = "CamelInfinispanOperation";
    String RESULT = "CamelInfinispanOperationResult";
    String RESULT_HEADER = "CamelInfinispanOperationResultHeader";
    String LIFESPAN_TIME = "CamelInfinispanLifespanTime";
    String LIFESPAN_TIME_UNIT = "CamelInfinispanTimeUnit";
    String MAX_IDLE_TIME = "CamelInfinispanMaxIdleTime";
    String MAX_IDLE_TIME_UNIT = "CamelInfinispanMaxIdleTimeUnit";
    String IGNORE_RETURN_VALUES = "CamelInfinispanIgnoreReturnValues";
    String EVENT_DATA = "CamelInfinispanEventData";
    String QUERY_BUILDER = "CamelInfinispanQueryBuilder";

    String CACHE_ENTRY_JOINING = "CacheEntryJoining";
    String CACHE_ENTRY_LEAVING = "CacheEntryLeaving";
    String CACHE_ENTRY_UPDATED = "CacheEntryUpdated";

}
