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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.infinispan.commons.api.BasicCacheContainer;

@UriParams
public class InfinispanConfiguration {
    @UriPath @Metadata(required = "true")
    private String host;
    @UriParam
    private BasicCacheContainer cacheContainer;
    @UriParam
    private String cacheName;
    @UriParam(label = "producer", defaultValue = "put", enums = "put,putAll,putIfAbsent,putAsync,putAllAsync,putIfAbsentAsync,get,containsKey,containsValue,remove,removeAsync,"
           + "replace,replaceAsync,clear,clearAsync,size")
    private String command;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean sync = true;
    @UriParam(label = "consumer", javaType = "java.lang.String")
    private Set<String> eventTypes;

    public String getCommand() {
        return command;
    }

    /**
     * The operation to perform.
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Specifies the host of the cache on Infinispan instance
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Specifies the cache Container to connect
     */   
    public BasicCacheContainer getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(BasicCacheContainer cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    /**
     * Specifies the cache name
     */  
    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
    * If true, the consumer will receive notifications synchronously
    */
    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public Set<String> getEventTypes() {
        return eventTypes;
    }

    /**
     * Specifies the set of event types to register by the consumer. Multiple event can be separated by comma.
     * <p/>
     * The possible event types are: CACHE_ENTRY_ACTIVATED, CACHE_ENTRY_PASSIVATED, CACHE_ENTRY_VISITED, CACHE_ENTRY_LOADED,
     * CACHE_ENTRY_EVICTED, CACHE_ENTRY_CREATED, CACHE_ENTRY_REMOVED, CACHE_ENTRY_MODIFIED, TRANSACTION_COMPLETED,
     * TRANSACTION_REGISTERED, CACHE_ENTRY_INVALIDATED, DATA_REHASHED, TOPOLOGY_CHANGED, PARTITION_STATUS_CHANGED
     */
    public void setEventTypes(Set<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * Specifies the set of event types to register by the consumer. Multiple event can be separated by comma.
     * <p/>
     * The possible event types are: CACHE_ENTRY_ACTIVATED, CACHE_ENTRY_PASSIVATED, CACHE_ENTRY_VISITED, CACHE_ENTRY_LOADED,
     * CACHE_ENTRY_EVICTED, CACHE_ENTRY_CREATED, CACHE_ENTRY_REMOVED, CACHE_ENTRY_MODIFIED, TRANSACTION_COMPLETED,
     * TRANSACTION_REGISTERED, CACHE_ENTRY_INVALIDATED, DATA_REHASHED, TOPOLOGY_CHANGED, PARTITION_STATUS_CHANGED
     */
    public void setEventTypes(String eventTypes) {
        this.eventTypes = new HashSet<String>(Arrays.asList(eventTypes.split(",")));
    }
}
