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
package org.apache.camel.component.cache;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.ServiceHelper;

public class CacheComponent extends UriEndpointComponent {
    private CacheConfiguration configuration;
    @Metadata(label = "advanced")
    private CacheManagerFactory cacheManagerFactory;
    @Metadata(defaultValue = "classpath:ehcache.xml")
    private String configurationFile;

    public CacheComponent() {
        super(CacheEndpoint.class);
        configuration = new CacheConfiguration();
    }

    public CacheComponent(CamelContext context) {
        super(context, CacheEndpoint.class);
        configuration = new CacheConfiguration();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");

        CacheConfiguration config = configuration.copy();
        setProperties(config, parameters);
        config.setCacheName(remaining);

        CacheEndpoint cacheEndpoint = new CacheEndpoint(uri, this, config, cacheManagerFactory);
        setProperties(cacheEndpoint, parameters);
        return cacheEndpoint;
    }

    public CacheManagerFactory getCacheManagerFactory() {
        return cacheManagerFactory;
    }

    /**
     * To use the given CacheManagerFactory for creating the CacheManager.
     * <p/>
     * By default the DefaultCacheManagerFactory is used.
     */
    public void setCacheManagerFactory(CacheManagerFactory cacheManagerFactory) {
        this.cacheManagerFactory = cacheManagerFactory;
    }

    public CacheConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the Cache configuration
     *
     * @param configuration the configuration to use by default for endpoints
     */
    public void setConfiguration(CacheConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public String getConfigurationFile() {
        return configurationFile;
    }

    /**
     * Sets the location of the <tt>ehcache.xml</tt> file to load from classpath or file system.
     * <p/>
     * By default the file is loaded from <tt>classpath:ehcache.xml</tt>
     */
    public void setConfigurationFile(String configurationFile) {
        this.configurationFile = configurationFile;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (cacheManagerFactory == null) {
            if (configurationFile != null) {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), configurationFile);
                cacheManagerFactory = new DefaultCacheManagerFactory(is, configurationFile);
            } else {
                cacheManagerFactory = new DefaultCacheManagerFactory();
            }
        }
        ServiceHelper.startService(cacheManagerFactory);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(cacheManagerFactory);
        super.doStop();
    }
}
