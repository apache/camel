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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.MutableContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;

/** Evaluate feature flags using the OpenFeature specification with flagd. */
@UriEndpoint(firstVersion = "4.23.0", scheme = "openfeature", title = "OpenFeature", syntax = "openfeature:domain",
             producerOnly = true, category = { Category.CORE })
public class OpenFeatureEndpoint extends DefaultEndpoint {

    private static final String DEFAULT_PROVIDER_BEAN = "flags";

    @UriPath
    @Metadata(required = true)
    private String domain;

    @UriParam
    private OpenFeatureConfiguration configuration;

    private volatile Client client;
    private volatile FeatureProvider resolvedProvider;
    private volatile File tempFlagFile;
    private volatile boolean ownedProvider;

    public OpenFeatureEndpoint(String uri, OpenFeatureComponent component, String domain,
                               OpenFeatureConfiguration configuration) {
        super(uri, component);
        this.domain = domain;
        this.configuration = configuration;
    }

    @Override
    public OpenFeatureComponent getComponent() {
        return (OpenFeatureComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() {
        return new OpenFeatureProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("OpenFeature is producer only");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        configuration.validate();

        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }

        FeatureProvider provider = resolveProvider();
        resolvedProvider = provider;
        client = getComponent().registerProviderAndGetClient(domain, provider);
    }

    @Override
    protected void doStop() throws Exception {
        client = null;

        if (ownedProvider && resolvedProvider != null) {
            resolvedProvider.shutdown();
        }
        resolvedProvider = null;

        File tmp = tempFlagFile;
        tempFlagFile = null;
        if (tmp != null) {
            Files.deleteIfExists(tmp.toPath());
        }

        super.doStop();
    }

    @SuppressWarnings("unchecked")
    MutableContext buildContext(Exchange exchange) {
        MutableContext ctx = new MutableContext();

        // 1. CamelOpenFeatureEvaluationContext header
        Object contextHeader = exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_CONTEXT);
        if (contextHeader == null) {
            contextHeader = exchange.getProperty(OpenFeatureConstants.EVALUATION_CONTEXT);
        }
        if (contextHeader instanceof Map) {
            addMapToContext(ctx, (Map<String, Object>) contextHeader);
        } else {
            // Fallback to body map entries
            Object body = exchange.getMessage().getBody();
            if (body instanceof Map) {
                addMapToContext(ctx, (Map<String, Object>) body);
            }
        }

        // 2. CamelOpenFeatureTargetingKey header or exchange property
        String targetingKey = exchange.getMessage().getHeader(OpenFeatureConstants.TARGETING_KEY, String.class);
        if (targetingKey == null) {
            targetingKey = exchange.getProperty(OpenFeatureConstants.TARGETING_KEY, String.class);
        }
        if (targetingKey != null) {
            ctx.setTargetingKey(targetingKey);
        }

        return ctx;
    }

    private static void addMapToContext(MutableContext ctx, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            if ("targetingKey".equals(key)) {
                ctx.setTargetingKey(value);
            } else {
                ctx.add(key, value);
            }
        }
    }

    String resolveFlagKey(Exchange exchange) {
        String key = exchange.getMessage().getHeader(OpenFeatureConstants.FLAG_KEY, String.class);
        if (key == null) {
            key = exchange.getProperty(OpenFeatureConstants.FLAG_KEY, String.class);
        }
        if (key == null) {
            key = configuration.getFlagKey();
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "No flag key specified. Set flagKey on the endpoint or provide it via the "
                                               + OpenFeatureConstants.FLAG_KEY + " header.");
        }
        return key;
    }

    public Object evaluate(Exchange exchange) {
        Client c = client;
        if (c == null) {
            throw new IllegalStateException("OpenFeature endpoint is not started");
        }
        String flagKey = resolveFlagKey(exchange);
        MutableContext ctx = buildContext(exchange);

        if (configuration.isBooleanEvaluation(exchange, ctx)) {
            boolean defaultVal = Boolean.parseBoolean(configuration.getDefaultValue());
            return c.getBooleanValue(flagKey, defaultVal, ctx);
        } else {
            return c.getStringValue(flagKey, configuration.getDefaultValue(), ctx);
        }
    }

    private FeatureProvider resolveProvider() throws IOException {
        // 1. Explicit provider bean reference
        String providerRef = configuration.getProvider();
        if (providerRef != null) {
            String beanName = providerRef.startsWith("#") ? providerRef.substring(1) : providerRef;
            FeatureProvider provider = getCamelContext().getRegistry()
                    .lookupByNameAndType(beanName, FeatureProvider.class);
            if (provider == null) {
                throw new IllegalArgumentException(
                        "No FeatureProvider bean found in the registry with name: " + beanName);
            }
            ownedProvider = false;
            return provider;
        }

        // 2. Registry lookup for default bean
        FeatureProvider defaultProvider = getCamelContext().getRegistry()
                .lookupByNameAndType(DEFAULT_PROVIDER_BEAN, FeatureProvider.class);
        if (defaultProvider != null) {
            ownedProvider = false;
            return defaultProvider;
        }

        // 3. Fallback to FlagdProvider
        ownedProvider = true;
        return createFlagdProvider();
    }

    private FeatureProvider createFlagdProvider() throws IOException {
        if (configuration.getFlags() != null) {
            return createFileProvider(configuration.getFlags());
        }
        if (configuration.getFlagsResource() != null) {
            String content = loadResource(configuration.getFlagsResource());
            return createFileProvider(content);
        }
        if (configuration.getHost() != null) {
            FlagdOptions options = FlagdOptions.builder()
                    .host(configuration.getHost())
                    .port(configuration.getPort())
                    .build();
            return new FlagdProvider(options);
        }
        throw new IllegalArgumentException(
                "No provider found. Set a provider bean reference, register a '" + DEFAULT_PROVIDER_BEAN
                                           + "' bean of type FeatureProvider, or configure flags, flagsResource, or host for the default flagd provider.");
    }

    private FlagdProvider createFileProvider(String flagContent) throws IOException {
        File tmp = File.createTempFile("camel-openfeature-", ".json");
        Files.writeString(tmp.toPath(), flagContent, StandardCharsets.UTF_8);
        tempFlagFile = tmp;

        FlagdOptions options = FlagdOptions.builder()
                .resolverType(Config.Resolver.FILE)
                .offlineFlagSourcePath(tmp.getAbsolutePath())
                .build();
        return new FlagdProvider(options);
    }

    private String loadResource(String location) throws IOException {
        try (InputStream input = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), location)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String getDomain() {
        return domain;
    }

    /** The OpenFeature domain to bind the provider to. */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public OpenFeatureConfiguration getConfiguration() {
        return configuration;
    }

    /** The OpenFeature configuration. */
    public void setConfiguration(OpenFeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getResultProperty() {
        return configuration.getResultProperty();
    }

    /** Store the evaluation result in this exchange property, preserving the original message body. */
    public void setResultProperty(String resultProperty) {
        configuration.setResultProperty(resultProperty);
    }

    public String getProvider() {
        return configuration.getProvider();
    }

    /**
     * Bean reference to a custom FeatureProvider (e.g. #myProvider). Mutually exclusive with flags and flagsResource.
     */
    public void setProvider(String provider) {
        configuration.setProvider(provider);
    }
}
