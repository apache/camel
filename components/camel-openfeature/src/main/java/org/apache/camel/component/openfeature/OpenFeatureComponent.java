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
import java.util.concurrent.ConcurrentHashMap;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("openfeature")
public class OpenFeatureComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFeatureComponent.class);

    @Metadata
    private OpenFeatureConfiguration configuration = new OpenFeatureConfiguration();

    private volatile OpenFeatureAPI api;
    private final Map<String, DomainBinding> domainBindings = new ConcurrentHashMap<>();

    private static class DomainBinding {
        final FeatureProvider provider;
        final boolean owned;
        int refCount;

        DomainBinding(FeatureProvider provider, boolean owned) {
            this.provider = provider;
            this.owned = owned;
            this.refCount = 1;
        }
    }

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
        OpenFeatureAPI localApi = api;
        api = null;
        domainBindings.clear();
        if (localApi != null) {
            try {
                localApi.shutdown();
            } catch (Exception e) {
                LOG.debug("Error shutting down OpenFeature API: {}", e.getMessage(), e);
            }
        }
        super.doStop();
    }

    synchronized boolean hasDomainBinding(String domain) {
        return domainBindings.containsKey(domain);
    }

    synchronized Client acquireClient(String domain) {
        DomainBinding existing = domainBindings.get(domain);
        if (existing == null) {
            throw new IllegalStateException("No binding exists for domain '" + domain + "'");
        }
        existing.refCount++;
        return api.getClient(domain);
    }

    synchronized Client registerEndpoint(String domain, FeatureProvider provider, boolean owned) throws Exception {
        api.setProviderAndWait(domain, provider);
        domainBindings.put(domain, new DomainBinding(provider, owned));
        return api.getClient(domain);
    }

    synchronized void unregisterEndpoint(String domain) {
        DomainBinding binding = domainBindings.get(domain);
        if (binding != null) {
            binding.refCount--;
            if (binding.refCount <= 0) {
                domainBindings.remove(domain);
                if (binding.owned) {
                    try {
                        binding.provider.shutdown();
                    } catch (Exception e) {
                        LOG.debug("Error shutting down owned provider for domain '{}': {}", domain, e.getMessage(), e);
                    }
                }
            }
        }
    }

    public OpenFeatureConfiguration getConfiguration() {
        return configuration;
    }

    /** Default configuration shared by OpenFeature endpoints. */
    public void setConfiguration(OpenFeatureConfiguration configuration) {
        this.configuration = configuration;
    }
}
