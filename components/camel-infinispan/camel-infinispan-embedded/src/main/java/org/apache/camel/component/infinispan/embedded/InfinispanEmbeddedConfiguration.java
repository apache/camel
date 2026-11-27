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

    private static final String COMPONENT_ONLY
            = " Read on the component only, as the cache manager is shared by all the endpoints of the component: set on an"
              + " endpoint, this option is not used.";

    @Metadata(autowired = true)
    @UriParam(label = "advanced",
              description = "The org.infinispan.configuration.cache.Configuration of the default cache, named default, of the"
                            + " cache manager that Camel creates when cacheContainer is not set. When set, configurationUri is"
                            + " not used." + COMPONENT_ONLY)
    private Configuration cacheContainerConfiguration;
    @Metadata(autowired = true)
    @UriParam(label = "advanced",
              description = "To use a custom org.infinispan.manager.EmbeddedCacheManager as the cache manager. When set or"
                            + " autowired, the cacheContainerConfiguration and configurationUri options are not used, and Camel"
                            + " does not stop the cache manager." + COMPONENT_ONLY)
    private EmbeddedCacheManager cacheContainer;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether Infinispan calls the listener of the consumer synchronously, so the cache operation that"
                            + " caused the event waits until the route has processed it. If false, Infinispan calls the listener"
                            + " in a thread of its own. Not used when customListener or queryBuilder is set.")
    private boolean sync = true;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer registers its listener as an Infinispan clustered listener, which receives"
                            + " the events of the changes made on all the nodes of the cluster, not only on the local node. A"
                            + " clustered listener receives only the events after a change, and only for entries that are"
                            + " created, modified, removed or expired. Not used when customListener or queryBuilder is set.")
    private boolean clusteredListener;
    @UriParam(label = "consumer",
              description = "The comma-separated names of the Infinispan event types that the consumer passes on to the route,"
                            + " for example CACHE_ENTRY_CREATED,CACHE_ENTRY_REMOVED. If not set, the consumer passes on all"
                            + " events. Its listener receives only the events of the types CACHE_ENTRY_ACTIVATED,"
                            + " CACHE_ENTRY_CREATED, CACHE_ENTRY_EXPIRED, CACHE_ENTRY_INVALIDATED, CACHE_ENTRY_LOADED,"
                            + " CACHE_ENTRY_MODIFIED, CACHE_ENTRY_PASSIVATED, CACHE_ENTRY_REMOVED and CACHE_ENTRY_VISITED."
                            + " Not used when customListener or queryBuilder is set.")
    private String eventTypes;
    @UriParam(label = "consumer",
              description = "To use a custom listener, a subclass of"
                            + " org.apache.camel.component.infinispan.embedded.InfinispanEmbeddedCustomListener annotated with"
                            + " the Infinispan @Listener annotation, that the consumer registers on the cache in place of its"
                            + " own listener. The eventTypes, sync and clusteredListener options are not used then. Not used"
                            + " when queryBuilder is set.")
    private InfinispanEmbeddedCustomListener customListener;
    @UriParam(label = "advanced", javaType = "java.lang.String",
              description = "A comma-separated list of names of org.infinispan.context.Flag, for example"
                            + " SKIP_CACHE_LOAD,SKIP_CACHE_STORE, that the endpoints apply to each cache that they use."
                            + COMPONENT_ONLY)
    private Flag[] flags;

    public EmbeddedCacheManager getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(EmbeddedCacheManager cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    public Configuration getCacheContainerConfiguration() {
        return cacheContainerConfiguration;
    }

    public void setCacheContainerConfiguration(Configuration cacheContainerConfiguration) {
        this.cacheContainerConfiguration = cacheContainerConfiguration;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isClusteredListener() {
        return clusteredListener;
    }

    public void setClusteredListener(boolean clusteredListener) {
        this.clusteredListener = clusteredListener;
    }

    public String getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

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
