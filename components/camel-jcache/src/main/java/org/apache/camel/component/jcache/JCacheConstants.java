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
package org.apache.camel.component.jcache;

import org.apache.camel.spi.Metadata;

public interface JCacheConstants {
    @Metadata(label = "producer", description = "The cache operation to perform", javaType = "String")
    String ACTION = "CamelJCacheAction";
    @Metadata(label = "producer", description = "The result of the cache operation", javaType = "boolean")
    String RESULT = "CamelJCacheResult";
    @Metadata(label = "consumer", description = "The type of event received", javaType = "String")
    String EVENT_TYPE = "CamelJCacheEventType";
    @Metadata(description = "The key of the cache entry", javaType = "Object")
    String KEY = "CamelJCacheKey";
    @Metadata(label = "producer", description = "The collection of keys against which the action should be performed",
              javaType = "Set<Object>")
    String KEYS = "CamelJCacheKeys";
    @Metadata(label = "consumer", description = "The old value of the cache entry", javaType = "Object")
    String OLD_VALUE = "CamelJCacheOldValue";
    @Metadata(label = "producer", description = "The EntryProcessor to invoke",
              javaType = "EntryProcessor<Object, Object, Object>")
    String ENTRY_PROCESSOR = "CamelJCacheEntryProcessor";
    @Metadata(label = "producer", description = "The additional arguments to pass to the EntryProcessor",
              javaType = "Collection<Object>")
    String ARGUMENTS = "CamelJCacheEntryArgs";
}
