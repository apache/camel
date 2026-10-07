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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Evaluate feature flags using the OpenFeature specification with flagd. */
@UriEndpoint(firstVersion = "4.23.0", scheme = "openfeature", title = "OpenFeature",
             syntax = "openfeature:domain/evaluationType",
             producerOnly = true, category = { Category.CLOUD }, headersClass = OpenFeatureConstants.class)
public class OpenFeatureEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFeatureEndpoint.class);
    private static final String DEFAULT_PROVIDER_BEAN = "flags";

    @UriPath
    @Metadata(required = true, description = "The OpenFeature domain to bind the provider to.")
    private String domain;

    @UriPath(enums = "boolean,variant,isEnabled",
             description = "The evaluation type. 'boolean' and 'isEnabled' use boolean evaluation (getBooleanValue)."
                           + " 'variant' uses string evaluation (getStringValue)."
                           + " When not set, the type is inferred from defaultValue.")
    private String evaluationType;

    @UriParam
    private OpenFeatureConfiguration configuration;

    private volatile Client client;
    private volatile File tempFlagFile;
    private volatile boolean ownedProvider;

    public OpenFeatureEndpoint(String uri, OpenFeatureComponent component, String domain, String evaluationType,
                               OpenFeatureConfiguration configuration) {
        super(uri, component);
        this.domain = domain;
        this.evaluationType = evaluationType;
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

        if (getComponent().hasDomainBinding(domain)) {
            client = getComponent().acquireClient(domain);
        } else {
            FeatureProvider provider = resolveProvider();
            client = getComponent().registerEndpoint(domain, provider, ownedProvider);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client = null;
            getComponent().unregisterEndpoint(domain);
        }
        ownedProvider = false;

        File tmp = tempFlagFile;
        tempFlagFile = null;
        if (tmp != null) {
            Files.deleteIfExists(tmp.toPath());
        }

        super.doStop();
    }

    /**
     * Evaluate a flag from the producer path. Reads flag key, evaluation type, targeting key and context from exchange
     * headers/properties/configuration. Sets result detail headers on the exchange.
     */
    public Object evaluate(Exchange exchange) {
        String flagKey = resolveFlagKey(exchange);
        String evalType = resolveEvaluationType(exchange);
        MutableContext ctx = buildContext(exchange);
        FlagEvaluationDetails<?> details = evaluateDetails(flagKey, evalType, ctx);
        setResultHeaders(exchange, details);
        return details.getValue();
    }

    /**
     * Evaluate a flag from the language path with explicit parameters. Does not read from or write to the exchange — no
     * side effects on headers or properties.
     */
    public Object evaluate(String flagKey, String evaluationType, String targetingKey, Map<String, Object> contextMap) {
        MutableContext ctx = buildMutableContext(targetingKey, contextMap);
        return evaluateDetails(flagKey, evaluationType, ctx).getValue();
    }

    private FlagEvaluationDetails<?> evaluateDetails(String flagKey, String evaluationType, MutableContext ctx) {
        Client c = client;
        if (c == null) {
            throw new IllegalStateException("OpenFeature endpoint is not started");
        }

        FlagEvaluationDetails<?> details;
        if (isBooleanEvaluation(evaluationType)) {
            boolean defaultVal = Boolean.parseBoolean(configuration.getDefaultValue());
            details = c.getBooleanDetails(flagKey, defaultVal, ctx);
        } else {
            details = c.getStringDetails(flagKey, configuration.getDefaultValue(), ctx);
        }

        if (details.getErrorCode() != null) {
            LOG.warn("OpenFeature evaluation error for flag '{}': {} - {}",
                    flagKey, details.getErrorCode(), details.getErrorMessage());
        }
        return details;
    }

    @SuppressWarnings("unchecked")
    MutableContext buildContext(Exchange exchange) {
        Map<String, Object> contextMap = null;

        Object contextHeader = exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_CONTEXT);
        if (contextHeader == null) {
            contextHeader = exchange.getProperty(OpenFeatureConstants.EVALUATION_CONTEXT);
        }
        if (contextHeader instanceof Map) {
            contextMap = (Map<String, Object>) contextHeader;
        } else if (configuration.isContextFromBody()) {
            Object body = exchange.getMessage().getBody();
            if (body instanceof Map) {
                contextMap = (Map<String, Object>) body;
            }
        }

        String targetingKey = exchange.getMessage().getHeader(OpenFeatureConstants.TARGETING_KEY, String.class);
        if (targetingKey == null) {
            targetingKey = exchange.getProperty(OpenFeatureConstants.TARGETING_KEY, String.class);
        }

        return buildMutableContext(targetingKey, contextMap);
    }

    static MutableContext buildMutableContext(String targetingKey, Map<String, Object> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            if (targetingKey != null) {
                return new MutableContext(targetingKey);
            }
            return new MutableContext();
        }

        Map<String, Value> attributes = new HashMap<>();
        String tk = targetingKey;
        for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
            if ("targetingKey".equals(entry.getKey())) {
                if (tk == null && entry.getValue() != null) {
                    tk = String.valueOf(entry.getValue());
                }
            } else {
                attributes.put(entry.getKey(), toValue(entry.getValue()));
            }
        }

        if (tk != null) {
            return new MutableContext(tk, attributes);
        }
        return new MutableContext(attributes);
    }

    @SuppressWarnings("unchecked")
    private static Value toValue(Object obj) {
        if (obj == null) {
            return new Value();
        }
        if (obj instanceof Boolean) {
            return new Value((Boolean) obj);
        }
        if (obj instanceof String) {
            return new Value((String) obj);
        }
        if (obj instanceof Integer) {
            return new Value((Integer) obj);
        }
        if (obj instanceof Long) {
            return new Value((Long) obj);
        }
        if (obj instanceof Double) {
            return new Value((Double) obj);
        }
        if (obj instanceof Number) {
            return new Value(((Number) obj).doubleValue());
        }
        if (obj instanceof Instant) {
            return new Value((Instant) obj);
        }
        if (obj instanceof Map) {
            Map<String, Value> attributes = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                attributes.put(String.valueOf(entry.getKey()), toValue(entry.getValue()));
            }
            return new Value(new MutableStructure(attributes));
        }
        if (obj instanceof List) {
            List<Value> values = new ArrayList<>();
            for (Object item : (List<?>) obj) {
                values.add(toValue(item));
            }
            return new Value(values);
        }
        return new Value(String.valueOf(obj));
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

    private String resolveEvaluationType(Exchange exchange) {
        String evalType = exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_TYPE, String.class);
        if (evalType == null) {
            evalType = exchange.getProperty(OpenFeatureConstants.EVALUATION_TYPE, String.class);
        }
        if (evalType == null) {
            evalType = evaluationType;
        }
        if (evalType == null) {
            evalType = configuration.getEvaluationType();
        }
        return evalType;
    }

    private boolean isBooleanEvaluation(String evalType) {
        if ("boolean".equalsIgnoreCase(evalType) || "isEnabled".equalsIgnoreCase(evalType)) {
            return true;
        }
        if ("variant".equalsIgnoreCase(evalType)) {
            return false;
        }
        String dv = configuration.getDefaultValue();
        return "true".equalsIgnoreCase(dv) || "false".equalsIgnoreCase(dv);
    }

    private void setResultHeaders(Exchange exchange, FlagEvaluationDetails<?> details) {
        exchange.getMessage().removeHeader(OpenFeatureConstants.EVALUATION_VARIANT);
        exchange.getMessage().removeHeader(OpenFeatureConstants.EVALUATION_REASON);
        exchange.getMessage().removeHeader(OpenFeatureConstants.EVALUATION_ERROR_CODE);
        if (details.getVariant() != null) {
            exchange.getMessage().setHeader(OpenFeatureConstants.EVALUATION_VARIANT, details.getVariant());
        }
        if (details.getReason() != null) {
            exchange.getMessage().setHeader(OpenFeatureConstants.EVALUATION_REASON, details.getReason());
        }
        if (details.getErrorCode() != null) {
            exchange.getMessage().setHeader(OpenFeatureConstants.EVALUATION_ERROR_CODE, details.getErrorCode().name());
        }
    }

    private FeatureProvider resolveProvider() throws IOException {
        String providerRef = configuration.getProvider();
        if (providerRef != null) {
            String beanName = providerRef.startsWith("#") ? providerRef.substring(1) : providerRef;
            FeatureProvider provider = getCamelContext().getRegistry()
                    .lookupByNameAndType(beanName, FeatureProvider.class);
            if (provider == null) {
                throw new IllegalArgumentException(
                        "No FeatureProvider bean found in the registry with name: " + beanName);
            }
            return provider;
        }

        FeatureProvider defaultProvider = getCamelContext().getRegistry()
                .lookupByNameAndType(DEFAULT_PROVIDER_BEAN, FeatureProvider.class);
        if (defaultProvider != null) {
            return defaultProvider;
        }

        return createFlagdProvider();
    }

    private FeatureProvider createFlagdProvider() throws IOException {
        ownedProvider = true;
        if (configuration.getFlags() != null) {
            return createFileProviderFromContent(configuration.getFlags());
        }
        if (configuration.getFlagsResource() != null) {
            String resource = configuration.getFlagsResource();
            if (resource.startsWith("file:")) {
                return createFileProviderFromPath(resource.substring(5));
            }
            String content = loadResource(resource);
            return createFileProviderFromContent(content);
        }
        if (configuration.getHost() != null) {
            FlagdOptions.FlagdOptionsBuilder builder = FlagdOptions.builder()
                    .host(configuration.getHost())
                    .port(configuration.getPort())
                    .deadline(configuration.getDeadline());
            if (configuration.isTls()) {
                builder.tls(true);
                if (configuration.getCertPath() != null) {
                    builder.certPath(configuration.getCertPath());
                }
            }
            return new FlagdProvider(builder.build());
        }
        throw new IllegalArgumentException(
                "No provider found. Set a provider bean reference, register a '" + DEFAULT_PROVIDER_BEAN
                                           + "' bean of type FeatureProvider, or configure flags, flagsResource, or host for the default flagd provider.");
    }

    private FlagdProvider createFileProviderFromPath(String path) {
        FlagdOptions options = FlagdOptions.builder()
                .resolverType(Config.Resolver.FILE)
                .offlineFlagSourcePath(path)
                .build();
        return new FlagdProvider(options);
    }

    private FlagdProvider createFileProviderFromContent(String flagContent) throws IOException {
        File tmp = Files.createTempFile("camel-openfeature-", ".json").toFile();
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
     * Bean reference to a custom FeatureProvider (e.g. #myProvider). When set, takes precedence over flags,
     * flagsResource, and host.
     */
    public void setProvider(String provider) {
        configuration.setProvider(provider);
    }
}
