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
package org.apache.camel.component.infinispan;

public interface InfinispanConstants {

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

    /**
     * @deprecated use {@link InfinispanOperation#PUT} instead.
     */
    @Deprecated
    String PUT = "CamelInfinispanOperationPut";

    /**
     * @deprecated use {@link InfinispanOperation#PUTASYNC} instead.
     */
    @Deprecated
    String PUT_ASYNC = "CamelInfinispanOperationPutAsync";

    /**
     * @deprecated use {@link InfinispanOperation#PUTIFABSENT} instead.
     */
    @Deprecated
    String PUT_IF_ABSENT = "CamelInfinispanOperationPutIfAbsent";

    /**
     * @deprecated use {@link InfinispanOperation#PUTIFABSENTASYNC} instead.
     */
    @Deprecated
    String PUT_IF_ABSENT_ASYNC = "CamelInfinispanOperationPutIfAbsentAsync";

    /**
     * @deprecated use {@link InfinispanOperation#GET} instead.
     */
    @Deprecated
    String GET = "CamelInfinispanOperationGet";

    /**
     * @deprecated use {@link InfinispanOperation#CONTAINSKEY} instead.
     */
    @Deprecated
    String CONTAINS_KEY = "CamelInfinispanOperationContainsKey";

    /**
     * @deprecated use {@link InfinispanOperation#CONTAINSVALUE} instead.
     */
    @Deprecated
    String CONTAINS_VALUE = "CamelInfinispanOperationContainsValue";

    /**
     * @deprecated use {@link InfinispanOperation#PUTALL} instead.
     */
    @Deprecated
    String PUT_ALL =  "CamelInfinispanOperationPutAll";

    /**
     * @deprecated use {@link InfinispanOperation#PUTALLASYNC} instead.
     */
    @Deprecated
    String PUT_ALL_ASYNC = "CamelInfinispanOperationPutAllAsync";

    /**
     * @deprecated use {@link InfinispanOperation#REMOVE} instead.
     */
    @Deprecated
    String REMOVE = "CamelInfinispanOperationRemove";

    /**
     * @deprecated use {@link InfinispanOperation#REMOVEASYNC} instead.
     */
    @Deprecated
    String REMOVE_ASYNC = "CamelInfinispanOperationRemoveAsync";

    /**
     * @deprecated use {@link InfinispanOperation#REPLACE} instead.
     */
    @Deprecated
    String REPLACE = "CamelInfinispanOperationReplace";

    /**
     * @deprecated use {@link InfinispanOperation#REPLACEASYNC} instead.
     */
    @Deprecated
    String REPLACE_ASYNC = "CamelInfinispanOperationReplaceAsync";

    /**
     * @deprecated use {@link InfinispanOperation#CLEAR} instead.
     */
    @Deprecated
    String CLEAR = "CamelInfinispanOperationClear";

    /**
     * @deprecated use {@link InfinispanOperation#CLEARASYNC} instead.
     */
    @Deprecated
    String CLEAR_ASYNC =  "CamelInfinispanOperationClearAsync";

    /**
     * @deprecated use {@link InfinispanOperation#SIZE} instead.
     */
    @Deprecated
    String SIZE = "CamelInfinispanOperationSize";

    /**
     * @deprecated use {@link InfinispanOperation#QUERY} instead.
     */
    @Deprecated
    String QUERY = "CamelInfinispanOperationQuery";

    /**
     * @deprecated use {@link InfinispanOperation#STATS} instead.
     */
    @Deprecated
    String STATS = "CamelInfinispanOperationStats";
}
