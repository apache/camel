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
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

@Metadata(label = "bean",
          description = "Infinispan based embedded cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
public class InfinispanEmbeddedClusterService extends InfinispanClusterService {

    @Metadata(description = "Node id", required = true)
    private String id;
    @Metadata(label = "advanced", description = "An implementation specific URI for the CacheManager",
              supportFileReference = true)
    private String configurationUri;
    @Metadata(label = "advanced", description = "To use an existing configuration")
    private InfinispanEmbeddedClusterConfiguration configuration;
    @Metadata(description = "Specifies the cache Container to connect")
    private EmbeddedCacheManager cacheContainer;
    @Metadata(label = "advanced", description = "The CacheContainer configuration. Used if the cacheContainer is not defined.")
    private Configuration cacheContainerConfiguration;
    @Metadata(description = "The lifespan of the cache entry for the local cluster member registered to the inventory",
              defaultValue = "30")
    private long lifespan;
    @Metadata(description = "The TimeUnit of the lifespan", defaultValue = "SECONDS")
    private TimeUnit lifespanTimeUnit;

    public InfinispanEmbeddedClusterService() {
    }

    public InfinispanEmbeddedClusterService(InfinispanEmbeddedClusterConfiguration configuration) {
        this.configuration = configuration.clone();
    }

    // *********************************************
    // Properties
    // *********************************************

    @Override
    public void setId(String id) {
        super.setId(id);
        this.id = id;
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    public InfinispanEmbeddedClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanEmbeddedClusterConfiguration configuration) {
        this.configuration = configuration;
    }

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

    public long getLifespan() {
        return lifespan;
    }

    public void setLifespan(long lifespan) {
        this.lifespan = lifespan;
    }

    public TimeUnit getLifespanTimeUnit() {
        return lifespanTimeUnit;
    }

    public void setLifespanTimeUnit(TimeUnit lifespanTimeUnit) {
        this.lifespanTimeUnit = lifespanTimeUnit;
    }

    // *********************************************
    // Impl
    // *********************************************

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (configuration == null) {
            configuration = new InfinispanEmbeddedClusterConfiguration();
            configuration.setConfigurationUri(configurationUri);
            configuration.setCacheContainer(cacheContainer);
            configuration.setCacheContainerConfiguration(cacheContainerConfiguration);
            if (lifespan != 0) {
                configuration.setLifespan(lifespan);
            }
            if (lifespanTimeUnit != null) {
                configuration.setLifespanTimeUnit(lifespanTimeUnit);
            }
        }
    }

    @Override
    protected InfinispanClusterView createView(String namespace) throws Exception {
        // Validate parameters
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(getId(), "Cluster ID");
        ObjectHelper.notNull(configuration, "Configuration");

        return new InfinispanEmbeddedClusterView(this, configuration, namespace);
    }
}
