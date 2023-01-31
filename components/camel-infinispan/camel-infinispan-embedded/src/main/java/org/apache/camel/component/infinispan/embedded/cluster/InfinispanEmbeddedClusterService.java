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
package org.apache.camel.component.infinispan.embedded.cluster;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterView;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanEmbeddedClusterService extends InfinispanClusterService {
    private InfinispanEmbeddedClusterConfiguration configuration;

    public InfinispanEmbeddedClusterService() {
        this.configuration = new InfinispanEmbeddedClusterConfiguration();
    }

    public InfinispanEmbeddedClusterService(InfinispanEmbeddedClusterConfiguration configuration) {
        this.configuration = configuration.clone();
    }

    // *********************************************
    // Properties
    // *********************************************

    public InfinispanEmbeddedClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanEmbeddedClusterConfiguration configuration) {
        this.configuration = configuration.clone();
    }

    public void setConfigurationUri(String configurationUri) {
        configuration.setConfigurationUri(configurationUri);
    }

    public EmbeddedCacheManager getCacheContainer() {
        return configuration.getCacheContainer();
    }

    public void setCacheContainer(EmbeddedCacheManager cacheContainer) {
        configuration.setCacheContainer(cacheContainer);
    }

    public Configuration getCacheContainerConfiguration() {
        return configuration.getCacheContainerConfiguration();
    }

    public void setCacheContainerConfiguration(Configuration cacheContainerConfiguration) {
        configuration.setCacheContainerConfiguration(cacheContainerConfiguration);
    }

    public long getLifespan() {
        return configuration.getLifespan();
    }

    public void setLifespan(long lifespan) {
        configuration.setLifespan(lifespan);
    }

    public TimeUnit getLifespanTimeUnit() {
        return configuration.getLifespanTimeUnit();
    }

    public void setLifespanTimeUnit(TimeUnit lifespanTimeUnit) {
        configuration.setLifespanTimeUnit(lifespanTimeUnit);
    }

    // *********************************************
    // Impl
    // *********************************************

    @Override
    protected InfinispanClusterView createView(String namespace) throws Exception {
        // Validate parameters
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(getId(), "Cluster ID");

        return new InfinispanEmbeddedClusterView(this, configuration, namespace);
    }
}
