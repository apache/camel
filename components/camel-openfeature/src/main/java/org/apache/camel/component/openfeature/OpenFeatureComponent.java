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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        File tempFlagFile;

        DomainBinding(FeatureProvider provider, boolean owned) {
            this.provider = provider;
            this.owned = owned;
            this.refCount = 1;
        }

        void shutdown() {
            if (owned) {
                try {
                    provider.shutdown();
                } catch (Exception e) {
                    LOG.debug("Error shutting down owned provider: {}", e.getMessage(), e);
                }
            }

            if (tempFlagFile != null) {
                try {
                    Files.deleteIfExists(tempFlagFile.toPath());
                } catch (IOException e) {
                    LOG.debug("Error deleting temp flag file: {}", e.getMessage(), e);
                }
                tempFlagFile = null;
            }
        }
    }

    static class ProviderRegistration {
        final FeatureProvider provider;
        final boolean owned;
        File tempFlagFile;

        ProviderRegistration(FeatureProvider provider, boolean owned) {
            this.provider = provider;
            this.owned = owned;
        }
    }

    @FunctionalInterface
    interface ProviderSupplier {
        ProviderRegistration get() throws Exception;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String domain = remaining;
        String evaluationType = null;
        int idx = remaining.indexOf('/');
        if (idx >= 0) {
            domain = remaining.substring(0, idx);
            String pathValue = remaining.substring(idx + 1);
            if (!pathValue.isBlank()) {
                evaluationType = pathValue;
            }
        }
        OpenFeatureEndpoint endpoint = new OpenFeatureEndpoint(uri, this, domain, evaluationType, configuration.copy());
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
        for (DomainBinding binding : domainBindings.values()) {
            binding.shutdown();
        }
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

    synchronized Client acquireOrRegister(String domain, ProviderSupplier providerSupplier) throws Exception {
        DomainBinding existing = domainBindings.get(domain);
        if (existing != null) {
            existing.refCount++;
            return api.getClient(domain);
        }

        ProviderRegistration reg = providerSupplier.get();
        try {
            api.setProviderAndWait(domain, reg.provider);
        } catch (Exception e) {
            if (reg.owned) {
                try {
                    reg.provider.shutdown();
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            if (reg.tempFlagFile != null) {
                try {
                    Files.deleteIfExists(reg.tempFlagFile.toPath());
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
        DomainBinding binding = new DomainBinding(reg.provider, reg.owned);
        binding.tempFlagFile = reg.tempFlagFile;
        domainBindings.put(domain, binding);
        return api.getClient(domain);
    }

    synchronized void unregisterEndpoint(String domain) {
        DomainBinding binding = domainBindings.get(domain);
        if (binding != null) {
            binding.refCount--;
            if (binding.refCount <= 0) {
                domainBindings.remove(domain);
                binding.shutdown();
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
