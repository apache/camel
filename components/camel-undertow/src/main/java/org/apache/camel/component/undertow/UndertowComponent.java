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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import io.undertow.server.HttpHandler;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link UndertowEndpoint}.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class UndertowComponent extends DefaultComponent implements RestConsumerFactory, RestApiConsumerFactory, RestProducerFactory, VerifiableComponent, SSLContextParametersAware {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowEndpoint.class);

    private Map<UndertowHostKey, UndertowHost> undertowRegistry = new ConcurrentHashMap<UndertowHostKey, UndertowHost>();

    @Metadata(label = "advanced")
    private UndertowHttpBinding undertowHttpBinding;
    @Metadata(label = "security")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced")
    private UndertowHostOptions hostOptions;

    public UndertowComponent() {
        this(null);
    }

    public UndertowComponent(CamelContext context) {
        super(context);

        registerExtension(UndertowComponentVerifierExtension::new);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Sa ve a copy of the parameters.
        Map<String, Object> healthCheckOptions = new HashMap<>(parameters);

        URI uriHttpUriAddress = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));
        URI endpointUri = URISupport.createRemainingURI(uriHttpUriAddress, parameters);

        // any additional channel options
        Map<String, Object> options = IntrospectionSupport.extractProperties(parameters, "option.");

        // determine sslContextParameters
        SSLContextParameters sslParams = this.sslContextParameters;
        if (sslParams == null) {
            sslParams = retrieveGlobalSslContextParameters();
        }

        // create the endpoint first
        UndertowEndpoint endpoint = createEndpointInstance(endpointUri, this);
        // set options from component
        endpoint.setSslContextParameters(sslParams);
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
                new URI(uriHttpUriAddress.getScheme(),
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

    protected UndertowEndpoint createEndpointInstance(URI endpointUri, UndertowComponent component) throws URISyntaxException {
        return new UndertowEndpoint(endpointUri.toString(), component);
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration, parameters, false);
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
                                      RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters, true);
    }

    Consumer doCreateConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                              String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api) throws Exception {
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
            config = camelContext.getRestConfiguration("undertow", true);
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
            if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                host = "0.0.0.0";
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("undertow")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        boolean explicitOptions = true;
        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);
        // allow OPTIONS in rest-dsl to allow clients to call the API and have responses with ALLOW headers
        if (!restrict.contains("OPTIONS")) {
            restrict += ",OPTIONS";
            // this is not an explicit OPTIONS path in the rest-dsl
            explicitOptions = false;
        }

        boolean cors = config.isEnableCORS();
        if (cors) {
            // allow HTTP Options as we want to handle CORS in rest-dsl
            map.put("optionsEnabled", "true");
        } else if (explicitOptions) {
            // the rest-dsl is using OPTIONS
            map.put("optionsEnabled", "true");
        }

        String query = URISupport.createQueryString(map);

        String url;
        if (api) {
            url = "undertow:%s://%s:%s/%s?matchOnUriPrefix=true&httpMethodRestrict=%s";
        } else {
            url = "undertow:%s://%s:%s/%s?matchOnUriPrefix=false&httpMethodRestrict=%s";
        }

        // get the endpoint
        url = String.format(url, scheme, host, port, path, restrict);

        if (!query.isEmpty()) {
            url = url + "&" + query;
        }

        UndertowEndpoint endpoint = camelContext.getEndpoint(url, UndertowEndpoint.class);
        setProperties(camelContext, endpoint, parameters);

        if (!map.containsKey("undertowHttpBinding")) {
            // use the rest binding, if not using a custom http binding
            endpoint.setUndertowHttpBinding(new RestUndertowHttpBinding());
        }

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }

        return consumer;
    }

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {

        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // get the endpoint
        String url = "undertow:" + host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        UndertowEndpoint endpoint = camelContext.getEndpoint(url, UndertowEndpoint.class);
        if (parameters != null && !parameters.isEmpty()) {
            setProperties(camelContext, endpoint, parameters);
        }
        String path = uriTemplate != null ? uriTemplate : basePath;
        endpoint.setHeaderFilterStrategy(new UndertowRestHeaderFilterStrategy(path, queryParameters));

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return endpoint.createProducer();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        RestConfiguration config = getCamelContext().getRestConfiguration("undertow", true);
        // configure additional options on undertow configuration
        if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
            setProperties(this, config.getComponentProperties());
        }
    }

    public HttpHandler registerEndpoint(HttpHandlerRegistrationInfo registrationInfo, SSLContext sslContext, HttpHandler handler) {
        final URI uri = registrationInfo.getUri();
        final UndertowHostKey key = new UndertowHostKey(uri.getHost(), uri.getPort(), sslContext);
        final UndertowHost host = undertowRegistry.computeIfAbsent(key, k -> createUndertowHost(k));

        host.validateEndpointURI(uri);
        return host.registerHandler(registrationInfo, handler);
    }

    public void unregisterEndpoint(HttpHandlerRegistrationInfo registrationInfo, SSLContext sslContext) {
        final URI uri = registrationInfo.getUri();
        final UndertowHostKey key = new UndertowHostKey(uri.getHost(), uri.getPort(), sslContext);
        final UndertowHost host = undertowRegistry.get(key);
        host.unregisterHandler(registrationInfo);
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

    @Override
    public ComponentVerifier getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
