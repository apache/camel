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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.context.Flag;

@UriParams
public class InfinispanConfiguration implements Cloneable {
    @UriParam
    private String hosts;
    @UriParam(label = "producer", defaultValue = "PUT")
    private InfinispanOperation operation = InfinispanOperation.PUT;
    @Deprecated
    @UriParam(label = "consumer", defaultValue = "PUT")
    private String command = "PUT";
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
    @UriParam(label = "advanced")
    private Map<String, String> configurationProperties;
    @UriParam(label = "advanced")
    private BasicCacheContainer cacheContainer;
    @UriParam(label = "advanced")
    private Object cacheContainerConfiguration;
    @UriParam(label = "advanced")
    private Object resultHeader;
    @UriParam(label = "advanced")
    private BiFunction remappingFunction;

    public String getCommand() {
        return operation.toString();
    }

    /**
     * The operation to perform.
     *
     * @deprecated replaced by @{link setOperation}
     */
    @Deprecated
    public void setCommand(String command) {
        if (command.startsWith(InfinispanConstants.OPERATION)) {
            command = command.substring(InfinispanConstants.OPERATION.length()).toUpperCase();
        }

        setOperation(InfinispanOperation.valueOf(command));
    }

    public InfinispanOperation getOperation() {
        return operation;
    }

    /**
     * The operation to perform.
     */
    public void setOperation(InfinispanOperation operation) {
        this.operation = operation;
    }

    public InfinispanOperation getOperationOrDefault() {
        return this.operation != null ? operation : InfinispanOperation.PUT;
    }

    /**
     * Specifies the host of the cache on Infinispan instance
     */
    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
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
     * Specifies the set of event types to register by the consumer. Multiple
     * event can be separated by comma.
     * <p/>
     * The possible event types are: CACHE_ENTRY_ACTIVATED,
     * CACHE_ENTRY_PASSIVATED, CACHE_ENTRY_VISITED, CACHE_ENTRY_LOADED,
     * CACHE_ENTRY_EVICTED, CACHE_ENTRY_CREATED, CACHE_ENTRY_REMOVED,
     * CACHE_ENTRY_MODIFIED, TRANSACTION_COMPLETED, TRANSACTION_REGISTERED,
     * CACHE_ENTRY_INVALIDATED, DATA_REHASHED, TOPOLOGY_CHANGED,
     * PARTITION_STATUS_CHANGED
     */
    public void setEventTypes(Set<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * Specifies the set of event types to register by the consumer. Multiple
     * event can be separated by comma.
     * <p/>
     * The possible event types are: CACHE_ENTRY_ACTIVATED,
     * CACHE_ENTRY_PASSIVATED, CACHE_ENTRY_VISITED, CACHE_ENTRY_LOADED,
     * CACHE_ENTRY_EVICTED, CACHE_ENTRY_CREATED, CACHE_ENTRY_REMOVED,
     * CACHE_ENTRY_MODIFIED, TRANSACTION_COMPLETED, TRANSACTION_REGISTERED,
     * CACHE_ENTRY_INVALIDATED, DATA_REHASHED, TOPOLOGY_CHANGED,
     * PARTITION_STATUS_CHANGED
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

    public Map<String, String> getConfigurationProperties() {
        return configurationProperties;
    }

    /**
     * Implementation specific properties for the CacheManager
     */
    public void setConfigurationProperties(Map<String, String> configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    /**
     * Adds an implementation specific property for the CacheManager
     */
    public void addConfigurationProperty(String key, String value) {
        if (this.configurationProperties == null) {
            this.configurationProperties = new HashMap<>();
        }

        this.configurationProperties.put(key, value);
    }

    public Object getCacheContainerConfiguration() {
        return cacheContainerConfiguration;
    }

    /**
     * The CacheContainer configuration. Uses if the cacheContainer is not
     * defined. Must be the following types:
     * org.infinispan.client.hotrod.configuration.Configuration - for remote
     * cache interaction configuration;
     * org.infinispan.configuration.cache.Configuration - for embedded cache
     * interaction configuration;
     */
    public void setCacheContainerConfiguration(Object cacheContainerConfiguration) {
        this.cacheContainerConfiguration = cacheContainerConfiguration;
    }

    public InfinispanConfiguration copy() {
        try {
            return (InfinispanConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public Object getResultHeader() {
        return resultHeader;
    }

    /**
     * Store the operation result in a header instead of the message body. By
     * default, resultHeader == null and the query result is stored in the
     * message body, any existing content in the message body is discarded. If
     * resultHeader is set, the value is used as the name of the header to store
     * the query result and the original message body is preserved. This value
     * can be overridden by an in message header named:
     * CamelInfinispanOperationResultHeader
     */
    public void setResultHeader(Object resultHeader) {
        this.resultHeader = resultHeader;
    }

    public BiFunction getRemappingFunction() {
        return remappingFunction;
    }

    /**
     * Set a specific remappingFunction to use in a compute operation
     */
    public void setRemappingFunction(BiFunction remappingFunction) {
        this.remappingFunction = remappingFunction;
    }
}
