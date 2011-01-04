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

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

public class CacheComponent extends DefaultComponent {
    private CacheConfiguration configuration;
    private CacheManagerFactory cacheManagerFactory = new CacheManagerFactory();
    
    public CacheComponent() {
        configuration = new CacheConfiguration();
    }

    public CacheComponent(CamelContext context) {
        super(context);
        configuration = new CacheConfiguration();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");
        CacheConfiguration config = configuration.copy();

        config.parseURI(new URI(uri));
        
        CacheEndpoint cacheEndpoint = new CacheEndpoint(uri, this, config, cacheManagerFactory);
        setProperties(cacheEndpoint.getConfig(), parameters);
        return cacheEndpoint;
    }

    public CacheManagerFactory getCacheManagerFactory() {
        return cacheManagerFactory;
    }

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
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(cacheManagerFactory);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(cacheManagerFactory);
        super.doStop();
    }
}
