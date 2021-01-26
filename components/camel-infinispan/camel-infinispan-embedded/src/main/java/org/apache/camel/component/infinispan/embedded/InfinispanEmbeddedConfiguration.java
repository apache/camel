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
package org.apache.camel.component.infinispan.embedded;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;

@UriParams
public class InfinispanEmbeddedConfiguration extends InfinispanConfiguration implements Cloneable {

    @Metadata(autowired = true)
    @UriParam(label = "advanced")
    private Configuration cacheContainerConfiguration;
    @Metadata(autowired = true)
    @UriParam(label = "advanced")
    private EmbeddedCacheManager cacheContainer;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean sync = true;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean clusteredListener;
    @UriParam(label = "consumer")
    private String eventTypes;
    @UriParam(label = "consumer")
    private InfinispanEmbeddedCustomListener customListener;
    @UriParam(label = "advanced", javaType = "java.lang.String")
    private Flag[] flags;

    /**
     * Specifies the cache Container to connect
     */
    public EmbeddedCacheManager getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(EmbeddedCacheManager cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    public Configuration getCacheContainerConfiguration() {
        return cacheContainerConfiguration;
    }

    /**
     * The CacheContainer configuration. Used if the cacheContainer is not defined.
     */
    public void setCacheContainerConfiguration(Configuration cacheContainerConfiguration) {
        this.cacheContainerConfiguration = cacheContainerConfiguration;
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

    public String getEventTypes() {
        return eventTypes;
    }

    /**
     * Specifies the set of event types to register by the consumer.Multiple event can be separated by comma.
     * <p/>
     * The possible event types are: CACHE_ENTRY_ACTIVATED, CACHE_ENTRY_PASSIVATED, CACHE_ENTRY_VISITED,
     * CACHE_ENTRY_LOADED, CACHE_ENTRY_EVICTED, CACHE_ENTRY_CREATED, CACHE_ENTRY_REMOVED, CACHE_ENTRY_MODIFIED,
     * TRANSACTION_COMPLETED, TRANSACTION_REGISTERED, CACHE_ENTRY_INVALIDATED, CACHE_ENTRY_EXPIRED, DATA_REHASHED,
     * TOPOLOGY_CHANGED, PARTITION_STATUS_CHANGED, PERSISTENCE_AVAILABILITY_CHANGED
     */
    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * Returns the custom listener in use, if provided
     */
    public InfinispanEmbeddedCustomListener getCustomListener() {
        return customListener;
    }

    public void setCustomListener(InfinispanEmbeddedCustomListener customListener) {
        this.customListener = customListener;
    }

    public boolean hasCustomListener() {
        return customListener != null;
    }

    public Flag[] getFlags() {
        return flags;
    }

    /**
     * A comma separated list of org.infinispan.context.Flag to be applied by default on each cache invocation
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

    @Override
    public InfinispanEmbeddedConfiguration clone() {
        try {
            return (InfinispanEmbeddedConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
