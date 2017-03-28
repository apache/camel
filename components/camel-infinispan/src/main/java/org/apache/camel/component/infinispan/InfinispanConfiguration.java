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
import org.apache.camel.util.ObjectHelper;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.context.Flag;

@UriParams
public class InfinispanConfiguration {
    @UriPath @Metadata(required = "true")
    private String host;
    @UriParam
    private BasicCacheContainer cacheContainer;
    @UriParam
    private String cacheName;
    @UriParam(label = "producer", defaultValue = "put", enums =
             "put,putAll,putIfAbsent,putAsync,putAllAsync,putIfAbsentAsync,"
           + "get,"
           + "containsKey,containsValue,"
           + "remove,removeAsync,"
           + "replace,replaceAsync,"
           + "size,"
           + "clear,clearAsync,"
           + "query,stats")
    private String command;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean sync = true;
    @UriParam(label = "consumer", javaType = "java.lang.String")
    private Set<String> eventTypes;
    @UriParam(label = "consumer")
    private InfinispanCustomListener customListener;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean clusteredListener;
    @UriParam
    private InfinispanQueryBuilder queryBuilder;
    @UriParam(label = "advanced", javaType = "java.lang.String")
    private Flag[] flags;
    @UriParam(label = "advanced")
    private String configurationUri;


    public String getCommand() {
        return command;
    }

    /**
     * The operation to perform.
     */
    public void setCommand(String command) {
        this.command = command;
    }

    public boolean hasCommand() {
        return ObjectHelper.isNotEmpty(command);
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

    /**
     * If true, the listener will be installed for the entire cluster
     */
    public boolean isClusteredListener() {
        return clusteredListener;
    }

    public void setClusteredListener(boolean clusteredListener) {
        this.clusteredListener = clusteredListener;
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
        this.eventTypes = new HashSet<>(Arrays.asList(eventTypes.split(",")));
    }

    /**
     * Returns the custom listener in use, if provided
     */
    public InfinispanCustomListener getCustomListener() {
        return customListener;
    }

    public void setCustomListener(InfinispanCustomListener customListener) {
        this.customListener = customListener;
    }

    public boolean hasCustomListener() {
        return customListener != null;
    }

    public InfinispanQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Specifies the query builder.
     */
    public void setQueryBuilder(InfinispanQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public boolean hasQueryBuilder() {
        return queryBuilder != null;
    }

    public Flag[] getFlags() {
        return flags;
    }

    /**
     * A comma separated list of Flag to be applied by default on each cache
     * invocation, not applicable to remote caches.
     */
    public void setFlags(String flagsAsString) {
        String[] flagsArray = flagsAsString.split(",");
        this.flags = new Flag[flagsArray.length];

        for (int i = 0; i < flagsArray.length; i++) {
            this.flags[i] = Flag.valueOf(flagsArray[i]);
        }
    }

    public void setFlags(Flag... flags) {
        this.flags = flags;
    }

    public boolean hasFlags() {
        return flags != null && flags.length > 0;
    }

    /**
     * An implementation specific URI for the CacheManager
     */
    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }
}
