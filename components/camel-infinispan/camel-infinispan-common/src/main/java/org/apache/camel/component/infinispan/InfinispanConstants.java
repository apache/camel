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

    @Metadata(label = "consumer", description = "The type of the received event.", javaType = "String")
    String EVENT_TYPE = "CamelInfinispanEventType";
    @Metadata(label = "consumer",
              description = "true if the notification is before the event has occurred, false if after the event has occurred.",
              javaType = "boolean", applicableFor = SCHEME_EMBEDDED)
    String IS_PRE = "CamelInfinispanIsPre";
    @Metadata(description = "The cache participating in the operation or event.", javaType = "String")
    String CACHE_NAME = "CamelInfinispanCacheName";
    @Metadata(description = "The key to perform the operation to or the key generating the event.", javaType = "Object")
    String KEY = "CamelInfinispanKey";
    @Metadata(label = "producer", description = "The value to use for the operation.", javaType = "Object")
    String VALUE = "CamelInfinispanValue";
    @Metadata(label = "producer", description = "The default value to use for a getOrDefault.", javaType = "Object")
    String DEFAULT_VALUE = "CamelInfinispanDefaultValue";
    @Metadata(label = "producer", description = "The old value to use for a replace.", javaType = "Object")
    String OLD_VALUE = "CamelInfinispanOldValue";
    @Metadata(label = "producer", description = "A Map to use in case of `CamelInfinispanOperationPutAll` operation",
              javaType = "Map")
    String MAP = "CamelInfinispanMap";
    @Metadata(label = "producer", description = "The operation to perform.",
              javaType = "org.apache.camel.component.infinispan.InfinispanOperation")
    String OPERATION = "CamelInfinispanOperation";
    @Metadata(label = "producer", description = "The name of the header whose value is the result", javaType = "String")
    String RESULT = "CamelInfinispanOperationResult";
    @Metadata(label = "producer", description = "Store the operation result in a header instead of the message body",
              javaType = "String")
    String RESULT_HEADER = "CamelInfinispanOperationResultHeader";
    @Metadata(label = "producer",
              description = "The Lifespan time of a value inside the cache. Negative values are interpreted as infinity.",
              javaType = "long")
    String LIFESPAN_TIME = "CamelInfinispanLifespanTime";
    @Metadata(label = "producer", description = "The Time Unit of an entry Lifespan Time.",
              javaType = "java.util.concurrent.TimeUnit")
    String LIFESPAN_TIME_UNIT = "CamelInfinispanTimeUnit";
    @Metadata(label = "producer",
              description = "The maximum amount of time an entry is allowed to be idle for before it is considered as expired.",
              javaType = "long")
    String MAX_IDLE_TIME = "CamelInfinispanMaxIdleTime";
    @Metadata(label = "producer", description = "The Time Unit of an entry Max Idle Time.",
              javaType = "java.util.concurrent.TimeUnit")
    String MAX_IDLE_TIME_UNIT = "CamelInfinispanMaxIdleTimeUnit";
    @Metadata(label = "consumer",
              description = "Signals that a write operation's return value will be ignored, so reading the existing value from a store or from a remote node is not necessary.",
              javaType = "boolean", defaultValue = "false", applicableFor = SCHEME_EMBEDDED)
    String IGNORE_RETURN_VALUES = "CamelInfinispanIgnoreReturnValues";
    @Metadata(label = "consumer", description = "The event data.", javaType = "Object")
    String EVENT_DATA = "CamelInfinispanEventData";
    @Metadata(label = "producer",
              description = "The QueryBuilder to use for QUERY command, if not present the command defaults to InifinispanConfiguration's one",
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
              description = "True if this event is generated from an existing entry as the listener has Listener.",
              javaType = "boolean", applicableFor = SCHEME_EMBEDDED)
    String CURRENT_STATE = "CamelInfinispanCurrentState";

    String CACHE_ENTRY_JOINING = "CacheEntryJoining";
    String CACHE_ENTRY_LEAVING = "CacheEntryLeaving";
    String CACHE_ENTRY_UPDATED = "CacheEntryUpdated";

}
