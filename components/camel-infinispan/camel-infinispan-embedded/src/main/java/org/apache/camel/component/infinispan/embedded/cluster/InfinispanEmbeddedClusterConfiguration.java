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

import org.apache.camel.component.infinispan.cluster.InfinispanClusterConfiguration;
import org.apache.camel.component.infinispan.embedded.InfinispanEmbeddedConfiguration;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanEmbeddedClusterConfiguration
        extends InfinispanClusterConfiguration<InfinispanEmbeddedConfiguration>
        implements Cloneable {

    public InfinispanEmbeddedClusterConfiguration() {
        super(new InfinispanEmbeddedConfiguration());
    }

    // ***********************************************
    // Properties
    // ***********************************************

    public EmbeddedCacheManager getCacheContainer() {
        return getConfiguration().getCacheContainer();
    }

    public void setCacheContainer(EmbeddedCacheManager cacheContainer) {
        getConfiguration().setCacheContainer(cacheContainer);
    }

    public Configuration getCacheContainerConfiguration() {
        return getConfiguration().getCacheContainerConfiguration();
    }

    public void setCacheContainerConfiguration(Configuration cacheContainerConfiguration) {
        getConfiguration().setCacheContainerConfiguration(cacheContainerConfiguration);
    }

    // ***********************************************
    //
    // ***********************************************

    @Override
    public InfinispanEmbeddedClusterConfiguration clone() {
        return (InfinispanEmbeddedClusterConfiguration) super.clone();
    }
}
