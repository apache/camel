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
package org.apache.camel.component.infinispan.remote.cluster;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterView;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;

public class InfinispanRemoteClusterService extends InfinispanClusterService {
    private InfinispanRemoteClusterConfiguration configuration;

    public InfinispanRemoteClusterService() {
        this.configuration = new InfinispanRemoteClusterConfiguration();
    }

    public InfinispanRemoteClusterService(InfinispanRemoteClusterConfiguration configuration) {
        this.configuration = configuration.clone();
    }

    // *********************************************
    // Properties
    // *********************************************

    public InfinispanRemoteClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanRemoteClusterConfiguration configuration) {
        this.configuration = configuration.clone();
    }

    public void setConfigurationUri(String configurationUri) {
        configuration.setConfigurationUri(configurationUri);
    }

    public RemoteCacheManager getCacheContainer() {
        return configuration.getCacheContainer();
    }

    public void setCacheContainer(RemoteCacheManager cacheContainer) {
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

        return new InfinispanRemoteClusterView(this, configuration, namespace);
    }
}
