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

import org.apache.camel.spi.Metadata;

public interface InfinispanConstants {

    String SCHEME_INFINISPAN = "infinispan";
    String SCHEME_EMBEDDED = "infinispan-embedded";

    String CACHE_MANAGER_CURRENT = "current";

    @Metadata(label = "consumer",
              description = "The type of the event that the consumer received: the name of the Infinispan event type, or"
                            + " CacheEntryJoining, CacheEntryUpdated or CacheEntryLeaving for a continuous query.",
              javaType = "String")
    String EVENT_TYPE = "CamelInfinispanEventType";
    @Metadata(label = "consumer",
              description = "true if the notification is before the event has occurred, false if after the event has occurred.",
              javaType = "boolean", applicableFor = SCHEME_EMBEDDED)
    String IS_PRE = "CamelInfinispanIsPre";
    @Metadata(description = "The name of the cache. The consumer sets it for each event, and for the producer it replaces the"
                            + " cache name of the endpoint.",
              javaType = "String")
    String CACHE_NAME = "CamelInfinispanCacheName";
    @Metadata(description = "The key of the entry. The consumer sets it for each event that has one. The producer uses it for"
                            + " the operations that take a key, unless the key option is set.",
              javaType = "Object")
    String KEY = "CamelInfinispanKey";
    @Metadata(label = "producer",
              description = "The value of the entry for the producer operations that take a value. It replaces the value"
                            + " option.",
              javaType = "Object")
    String VALUE = "CamelInfinispanValue";
    @Metadata(label = "producer",
              description = "The value that the GETORDEFAULT operation returns when the key is not in the cache. It replaces"
                            + " the defaultValue option.",
              javaType = "Object")
    String DEFAULT_VALUE = "CamelInfinispanDefaultValue";
    @Metadata(label = "producer",
              description = "The value that the REPLACE and REPLACEASYNC operations compare with the current value of the"
                            + " entry, to replace the entry only if they are equal. It replaces the oldValue option. The consumer"
                            + " of infinispan-embedded sets it to the value of the removed entry for a CACHE_ENTRY_REMOVED"
                            + " event.",
              javaType = "Object")
    String OLD_VALUE = "CamelInfinispanOldValue";
    @Metadata(label = "producer",
              description = "The java.util.Map of the entries that the PUTALL and PUTALLASYNC operations store.",
              javaType = "Map")
    String MAP = "CamelInfinispanMap";
    @Metadata(label = "producer", description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "org.apache.camel.component.infinispan.InfinispanOperation")
    String OPERATION = "CamelInfinispanOperation";
    @Metadata(label = "producer", description = "The name of the header whose value is the result", javaType = "String",
              deprecationNote = "Never set nor read by the component. Use CamelInfinispanOperationResultHeader to choose"
                                + " the header that carries the result of an operation.")
    @Deprecated
    String RESULT = "CamelInfinispanOperationResult";
    @Metadata(label = "producer",
              description = "The name of the header in which the producer stores the result of the operation, keeping the"
                            + " message body. It replaces the resultHeader option.",
              javaType = "String")
    String RESULT_HEADER = "CamelInfinispanOperationResultHeader";
    @Metadata(label = "producer",
              description = "The lifespan of the entry, after which it expires, for the PUT, PUTALL, PUTIFABSENT and REPLACE"
                            + " operations and their ASYNC variants. Only used together with the CamelInfinispanTimeUnit"
                            + " header. A negative value means that the entry does not expire.",
              javaType = "long")
    String LIFESPAN_TIME = "CamelInfinispanLifespanTime";
    @Metadata(label = "producer", description = "The java.util.concurrent.TimeUnit of the CamelInfinispanLifespanTime header.",
              javaType = "java.util.concurrent.TimeUnit")
    String LIFESPAN_TIME_UNIT = "CamelInfinispanTimeUnit";
    @Metadata(label = "producer",
              description = "The maximum time that the entry can stay without being accessed before it expires, for the same"
                            + " operations as the CamelInfinispanLifespanTime header. Only used together with the"
                            + " CamelInfinispanMaxIdleTimeUnit header, and only when the CamelInfinispanLifespanTime and"
                            + " CamelInfinispanTimeUnit headers are set too.",
              javaType = "long")
    String MAX_IDLE_TIME = "CamelInfinispanMaxIdleTime";
    @Metadata(label = "producer", description = "The java.util.concurrent.TimeUnit of the CamelInfinispanMaxIdleTime header.",
              javaType = "java.util.concurrent.TimeUnit")
    String MAX_IDLE_TIME_UNIT = "CamelInfinispanMaxIdleTimeUnit";
    @Metadata(label = "producer",
              description = "Whether the producer applies the Infinispan IGNORE_RETURN_VALUES flag to the cache, so that a"
                            + " write operation does not need to read the existing value from a cache store or from another"
                            + " node. The result of such an operation, for example the previous value for PUT, can then be"
                            + " null.",
              javaType = "boolean", defaultValue = "false", applicableFor = SCHEME_EMBEDDED)
    String IGNORE_RETURN_VALUES = "CamelInfinispanIgnoreReturnValues";
    @Metadata(label = "consumer",
              description = "The value of the entry that the event is about, when the event carries one. For a continuous"
                            + " query, it is the value of the entry that joins the result or is updated.",
              javaType = "Object")
    String EVENT_DATA = "CamelInfinispanEventData";
    @Metadata(label = "producer",
              description = "The org.apache.camel.component.infinispan.InfinispanQueryBuilder that builds the query of the"
                            + " QUERY operation. It replaces the queryBuilder option.",
              javaType = "org.apache.camel.component.infinispan.InfinispanQueryBuilder")
    String QUERY_BUILDER = "CamelInfinispanQueryBuilder";
    @Metadata(label = "consumer", description = "Provides access to the version of the created cache entry.", javaType = "long",
              applicableFor = SCHEME_INFINISPAN)
    String ENTRY_VERSION = "CamelInfinispanEntryVersion";
    @Metadata(label = "consumer",
              description = "This will be true if the write command that caused this had to be retried again due to a topology change.",
              javaType = "boolean")
    String COMMAND_RETRIED = "CamelInfinispanCommandRetried";
    @Metadata(label = "consumer",
              description = "Indicates whether the cache entry modification event is the result of the cache entry being created.",
              javaType = "boolean", applicableFor = SCHEME_EMBEDDED)
    String ENTRY_CREATED = "CamelInfinispanEntryCreated";
    @Metadata(label = "consumer",
              description = "true if the call originated on the local cache instance; false if originated from a remote one.",
              javaType = "boolean", applicableFor = SCHEME_EMBEDDED)
    String ORIGIN_LOCAL = "CamelInfinispanOriginLocal";
    @Metadata(label = "consumer",
              description = "Whether Infinispan generated the event for an entry that already existed when the listener was"
                            + " registered, which it does only for a listener whose @Listener annotation sets includeCurrentState"
                            + " to true.",
              javaType = "boolean", applicableFor = SCHEME_EMBEDDED)
    String CURRENT_STATE = "CamelInfinispanCurrentState";

    String CACHE_ENTRY_JOINING = "CacheEntryJoining";
    String CACHE_ENTRY_LEAVING = "CacheEntryLeaving";
    String CACHE_ENTRY_UPDATED = "CacheEntryUpdated";

}
