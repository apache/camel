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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import io.undertow.server.HttpHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.RestComponentHelper;
import org.apache.camel.support.RestProducerFactoryHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.http.HttpUtil.recreateUrl;

/**
 * Represents the component that manages {@link UndertowEndpoint}.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("undertow")
public class UndertowComponent extends DefaultComponent
        implements RestConsumerFactory, RestApiConsumerFactory, RestProducerFactory, SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowComponent.class);

    private final Map<UndertowHostKey, UndertowHost> undertowRegistry = new ConcurrentHashMap<>();
    private final Set<HttpHandlerRegistrationInfo> handlers = new LinkedHashSet<>();

    @Metadata(label = "advanced")
    private UndertowHttpBinding undertowHttpBinding;
    @Metadata(label = "security")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced")
    private UndertowHostOptions hostOptions;
    @Metadata(label = "consumer")
    private boolean muteException;
    @Metadata(label = "security")
    private Object securityConfiguration;
    @Metadata(label = "security")
    private String allowedRoles;
    @Metadata(label = "security")
    private UndertowSecurityProvider securityProvider;

    public UndertowComponent() {
        this(null);
    }

    public UndertowComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        URI uriHttpUriAddress = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));
        URI endpointUri = URISupport.createRemainingURI(uriHttpUriAddress, parameters);

        // any additional channel options
        Map<String, Object> options = PropertiesHelper.extractProperties(parameters, "option.");

        // determine sslContextParameters
        SSLContextParameters sslParams = this.sslContextParameters;
        if (sslParams == null) {
            sslParams = retrieveGlobalSslContextParameters();
        }

        // create the endpoint first
        UndertowEndpoint endpoint = createEndpointInstance(endpointUri, this);
        // set options from component
        endpoint.setSslContextParameters(sslParams);
        endpoint.setMuteException(muteException);
        // Prefer endpoint configured over component configured
        if (undertowHttpBinding == null) {
            // fallback to component configured
            undertowHttpBinding = getUndertowHttpBinding();
        }
        if (undertowHttpBinding != null) {
            endpoint.setUndertowHttpBinding(undertowHttpBinding);
        }
        // set options from parameters
        setProperties(endpoint, parameters);
        if (options != null) {
            endpoint.setOptions(options);
        }

        // then re-create the http uri with the remaining parameters which the endpoint did not use
        URI httpUri = URISupport.createRemainingURI(
                new URI(
                        uriHttpUriAddress.getScheme(),
                        uriHttpUriAddress.getUserInfo(),
                        uriHttpUriAddress.getHost(),
                        uriHttpUriAddress.getPort(),
                        uriHttpUriAddress.getPath(),
                        uriHttpUriAddress.getQuery(),
                        uriHttpUriAddress.getFragment()),
                parameters);
        endpoint.setHttpURI(httpUri);

        return endpoint;
    }

    protected UndertowEndpoint createEndpointInstance(URI endpointUri, UndertowComponent component) {
        return new UndertowEndpoint(endpointUri.toString(), component);
    }

    @Override
    public Consumer createConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration,
                parameters, false);
    }

    @Override
    public Consumer createApiConsumer(
            CamelContext camelContext, Processor processor, String contextPath,
            RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters, true);
    }

    private void initSecurityProvider() throws Exception {
        Object securityConfiguration = getSecurityConfiguration();
        if (securityConfiguration != null) {
            ServiceLoader<UndertowSecurityProvider> securityProvider = ServiceLoader.load(UndertowSecurityProvider.class);

            Iterator<UndertowSecurityProvider> iter = securityProvider.iterator();
            List<String> providers = new LinkedList();
            while (iter.hasNext()) {
                UndertowSecurityProvider security = iter.next();
                //only securityProvider, who accepts security configuration, could be used
                if (security.acceptConfiguration(securityConfiguration, null)) {
                    this.securityProvider = security;
                    LOG.info("Security provider found {}", securityProvider.getClass().getName());
                    break;
                }
                providers.add(security.getClass().getName());
            }
            if (this.securityProvider == null) {
                LOG.info("Security provider for configuration {} not found {}", securityConfiguration, providers);
            }
        }
    }

    Consumer doCreateConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api)
            throws Exception {
        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);
        String scheme = "http";
        String host = "";
        int port = 0;

        RestConfiguration config = configuration;
        if (config == null) {
            config = CamelContextHelper.getRestConfiguration(camelContext, getComponentName());

        }
        if (config.getScheme() != null) {
            scheme = config.getScheme();
        }
        if (config.getHost() != null) {
            host = config.getHost();
        }
        int num = config.getPort();
        if (num > 0) {
            port = num;
        }

        // prefix path with context-path if configured in rest-dsl configuration
        String contextPath = config.getContextPath();
        if (ObjectHelper.isNotEmpty(contextPath)) {
            contextPath = FileUtil.stripTrailingSeparator(contextPath);
            contextPath = FileUtil.stripLeadingSeparator(contextPath);
            if (ObjectHelper.isNotEmpty(contextPath)) {
                path = contextPath + "/" + path;
            }
        }

        // if no explicit hostname set then resolve the hostname
        if (ObjectHelper.isEmpty(host)) {
            host = RestComponentHelper.resolveRestHostName(host, config);
        }

        Map<String, Object> map = RestComponentHelper.initRestEndpointProperties(getComponentName(), config);
        // build query string, and append any endpoint configuration properties

        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);

        boolean explicitOptions = restrict.contains("OPTIONS");
        boolean cors = config.isEnableCORS();

        if (cors) {
            // allow HTTP Options as we want to handle CORS in rest-dsl
            map.put("optionsEnabled", "true");
        } else if (explicitOptions) {
            // the rest-dsl is using OPTIONS
            map.put("optionsEnabled", "true");
        }

        if (api) {
            map.put("matchOnUriPrefix", "true");
        }

        RestComponentHelper.addHttpRestrictParam(map, verb, !explicitOptions);

        String url = RestComponentHelper.createRestConsumerUrl(getComponentName(), scheme, host, port, path, map);

        UndertowEndpoint endpoint = (UndertowEndpoint) camelContext.getEndpoint(url, parameters);

        if (!map.containsKey("undertowHttpBinding")) {
            // use the rest binding, if not using a custom http binding
            endpoint.setUndertowHttpBinding(new RestUndertowHttpBinding(endpoint.isUseStreaming()));
        }

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }
        if (consumer instanceof UndertowConsumer) {
            // mark the consumer as a rest consumer
            ((UndertowConsumer) consumer).setRest(true);
        }

        return consumer;
    }

    @Override
    public Producer createProducer(
            CamelContext camelContext, String host,
            String verb, String basePath, String uriTemplate, String queryParameters,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {

        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // get the endpoint
        String url = getComponentName() + ":" + host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        RestConfiguration config = CamelContextHelper.getRestConfiguration(camelContext, null, getComponentName());

        Map<String, Object> map = new HashMap<>();
        // build query string, and append any endpoint configuration properties
        if (config.getProducerComponent() == null || config.getProducerComponent().equals(getComponentName())) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        url = recreateUrl(map, url);

        parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

        // there are cases where we might end up here without component being created beforehand
        // we need to abide by the component properties specified in the parameters when creating
        // the component
        RestProducerFactoryHelper.setupComponentFor(url, camelContext, (Map<String, Object>) parameters.remove("component"));

        UndertowEndpoint endpoint = (UndertowEndpoint) camelContext.getEndpoint(url, parameters);
        String path = uriTemplate != null ? uriTemplate : basePath;
        endpoint.setHeaderFilterStrategy(new UndertowRestHeaderFilterStrategy(path, queryParameters));

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return endpoint.createProducer();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (this.securityProvider == null) {
            initSecurityProvider();
        }

        try {
            RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), getComponentName());

            // configure additional options on undertow configuration
            if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
                setProperties(this, config.getComponentProperties());
            }
        } catch (IllegalArgumentException e) {
            // if there's a mismatch between the component and the rest-configuration,
            // then getRestConfiguration throws IllegalArgumentException which can be
            // safely ignored as it means there's no special conf for this component.
        }
    }

    public HttpHandler registerEndpoint(
            UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo, SSLContext sslContext, HttpHandler handler)
            throws Exception {
        final URI uri = registrationInfo.getUri();
        final UndertowHostKey key = new UndertowHostKey(uri.getHost(), uri.getPort(), sslContext);
        final UndertowHost host = undertowRegistry.computeIfAbsent(key, this::createUndertowHost);

        host.validateEndpointURI(uri);
        handlers.add(registrationInfo);

        HttpHandler handlerWrapped = handler;
        if (this.securityProvider != null) {
            handlerWrapped = this.securityProvider.wrapHttpHandler(handler);
        }

        return host.registerHandler(consumer, registrationInfo, handlerWrapped);
    }

    public void unregisterEndpoint(
            UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo, SSLContext sslContext) {
        final URI uri = registrationInfo.getUri();
        final UndertowHostKey key = new UndertowHostKey(uri.getHost(), uri.getPort(), sslContext);
        final UndertowHost host = undertowRegistry.get(key);

        handlers.remove(registrationInfo);

        // if the route is not automatically started, then the undertow registry
        // may not have any instance of UndertowHost associated to the given
        // registrationInfo
        if (host != null) {
            host.unregisterHandler(consumer, registrationInfo);
        }
    }

    protected UndertowHost createUndertowHost(UndertowHostKey key) {
        return new DefaultUndertowHost(key, hostOptions);
    }

    public UndertowHttpBinding getUndertowHttpBinding() {
        return undertowHttpBinding;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setUndertowHttpBinding(UndertowHttpBinding undertowHttpBinding) {
        this.undertowHttpBinding = undertowHttpBinding;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public UndertowHostOptions getHostOptions() {
        return hostOptions;
    }

    /**
     * To configure common options, such as thread pools
     */
    public void setHostOptions(UndertowHostOptions hostOptions) {
        this.hostOptions = hostOptions;
    }

    public boolean isMuteException() {
        return muteException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side the response's body won't contain the
     * exception's stack trace.
     */
    public void setMuteException(boolean muteException) {
        this.muteException = muteException;
    }

    public ComponentVerifierExtension getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class)
                .orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }

    protected String getComponentName() {
        return "undertow";
    }

    public Set<HttpHandlerRegistrationInfo> getHandlers() {
        return handlers;
    }

    public Object getSecurityConfiguration() {
        return securityConfiguration;
    }

    /**
     * Configuration used by UndertowSecurityProvider. Security configuration object for use from
     * UndertowSecurityProvider. Configuration is UndertowSecurityProvider specific. Each provider decides, whether it
     * accepts configuration.
     */
    public void setSecurityConfiguration(Object securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    /**
     * Configuration used by UndertowSecurityProvider. Comma separated list of allowed roles.
     */
    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    /**
     * Security provider allows plug in the provider, which will be used to secure requests. SPI approach could be used
     * too (component then finds security provider using SPI).
     */
    public void setSecurityProvider(UndertowSecurityProvider securityProvider) {
        this.securityProvider = securityProvider;
    }

    public UndertowSecurityProvider getSecurityProvider() {
        return securityProvider;
    }
}
