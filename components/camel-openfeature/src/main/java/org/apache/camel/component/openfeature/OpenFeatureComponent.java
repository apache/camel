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
package org.apache.camel.component.openfeature;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("openfeature")
public class OpenFeatureComponent extends DefaultComponent {

    @Metadata
    private OpenFeatureConfiguration configuration = new OpenFeatureConfiguration();

    private volatile OpenFeatureAPI api;
    private final Set<String> initializedDomains = ConcurrentHashMap.newKeySet();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        OpenFeatureEndpoint endpoint = new OpenFeatureEndpoint(uri, this, remaining, configuration.copy());
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        api = OpenFeatureAPI.createIsolated();
    }

    @Override
    protected void doStop() throws Exception {
        OpenFeatureAPI a = api;
        api = null;
        initializedDomains.clear();
        if (a != null) {
            a.shutdown();
        }
        super.doStop();
    }

    synchronized Client registerProviderAndGetClient(String domain, FeatureProvider provider) throws Exception {
        if (initializedDomains.add(domain)) {
            api.setProviderAndWait(domain, provider);
        }
        return api.getClient(domain);
    }

    public OpenFeatureConfiguration getConfiguration() {
        return configuration;
    }

    /** Default configuration shared by OpenFeature endpoints. */
    public void setConfiguration(OpenFeatureConfiguration configuration) {
        this.configuration = configuration;
    }
}
