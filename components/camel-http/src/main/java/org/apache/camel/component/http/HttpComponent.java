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
package org.apache.camel.component.http;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpRestHeaderFilterStrategy;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.RestProducerFactoryHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the HTTP Component
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("http,https")
public class HttpComponent extends HttpCommonComponent implements RestProducerFactory, SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(HttpComponent.class);

    @Metadata(label = "advanced", description = "To use the custom HttpClientConfigurer to perform configuration of the HttpClient that will be used.")
    protected HttpClientConfigurer httpClientConfigurer;
    @Metadata(label = "advanced", description = "To use a custom and shared HttpClientConnectionManager to manage connections."
        + " If this has been configured then this is always used for all endpoints created by this component.")
    protected HttpClientConnectionManager clientConnectionManager;
    @Metadata(label = "advanced", description = "To use a custom org.apache.http.protocol.HttpContext when executing requests.")
    protected HttpContext httpContext;
    @Metadata(label = "security", description = "To configure security using SSLContextParameters."
        + " Important: Only one instance of org.apache.camel.support.jsse.SSLContextParameters is supported per HttpComponent."
        + " If you need to use 2 or more different instances, you need to define a new HttpComponent per instance you need.")
    protected SSLContextParameters sslContextParameters;
    @Metadata(label = "security", description = "To use a custom X509HostnameVerifier such as DefaultHostnameVerifier or NoopHostnameVerifier.")
    protected HostnameVerifier x509HostnameVerifier = new DefaultHostnameVerifier();
    @Metadata(label = "producer", description = "To use a custom org.apache.http.client.CookieStore."
        + " By default the org.apache.http.impl.client.BasicCookieStore is used which is an in-memory only cookie store."
        + " Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie"
        + " shouldn't be stored as we are just bridging (eg acting as a proxy).")
    protected CookieStore cookieStore;

    // timeout
    @Metadata(label = "timeout", defaultValue = "-1", description = "The timeout in milliseconds used when requesting a connection"
        + " from the connection manager. A timeout value of zero is interpreted as an infinite timeout."
        + " A timeout value of zero is interpreted as an infinite timeout."
        + " A negative value is interpreted as undefined (system default).")
    protected int connectionRequestTimeout = -1;
    @Metadata(label = "timeout", defaultValue = "-1", description = "Determines the timeout in milliseconds until a connection is established."
        + " A timeout value of zero is interpreted as an infinite timeout."
        + " A timeout value of zero is interpreted as an infinite timeout."
        + " A negative value is interpreted as undefined (system default).")
    protected int connectTimeout = -1;
    @Metadata(label = "timeout", defaultValue = "-1", description = "Defines the socket timeout in milliseconds,"
        + " which is the timeout for waiting for data  or, put differently,"
        + " a maximum period inactivity between two consecutive data packets)."
        + " A timeout value of zero is interpreted as an infinite timeout."
        + " A negative value is interpreted as undefined (system default).")
    protected int socketTimeout = -1;

    // options to the default created http connection manager
    @Metadata(label = "advanced", defaultValue = "200", description = "The maximum number of connections.")
    protected int maxTotalConnections = 200;
    @Metadata(label = "advanced", defaultValue = "20", description = "The maximum number of connections per route.")
    protected int connectionsPerRoute = 20;
    // It's MILLISECONDS, the default value is always keep alive
    @Metadata(label = "advanced", description = "The time for connection to live, the time unit is millisecond, the default value is always keep alive.")
    protected long connectionTimeToLive = -1;
    @Metadata(label = "security", defaultValue = "false", description = "Enable usage of global SSL context parameters.")
    private boolean useGlobalSslContextParameters;

    public HttpComponent() {
        this(HttpEndpoint.class);
    }

    public HttpComponent(Class<? extends HttpEndpoint> endpointClass) {
        registerExtension(HttpComponentVerifierExtension::new);
    }

    /**
     * Creates the HttpClientConfigurer based on the given parameters
     *
     * @param parameters the map of parameters
     * @param secure whether the endpoint is secure (eg https)
     * @return the configurer
     * @throws Exception is thrown if error creating configurer
     */
    protected HttpClientConfigurer createHttpClientConfigurer(Map<String, Object> parameters, boolean secure) throws Exception {
        // prefer to use endpoint configured over component configured
        HttpClientConfigurer configurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurer", HttpClientConfigurer.class);
        if (configurer == null) {
            // fallback to component configured
            configurer = getHttpClientConfigurer();
        }

        configurer = configureBasicAuthentication(parameters, configurer);
        configurer = configureHttpProxy(parameters, configurer, secure);

        return configurer;
    }

    private HttpClientConfigurer configureBasicAuthentication(Map<String, Object> parameters, HttpClientConfigurer configurer) {
        String authUsername = getParameter(parameters, "authUsername", String.class);
        String authPassword = getParameter(parameters, "authPassword", String.class);

        if (authUsername != null && authPassword != null) {
            String authDomain = getParameter(parameters, "authDomain", String.class);
            String authHost = getParameter(parameters, "authHost", String.class);

            return CompositeHttpConfigurer.combineConfigurers(configurer, new BasicAuthenticationHttpClientConfigurer(authUsername, authPassword, authDomain, authHost));
        } else if (this.httpConfiguration != null) {
            if ("basic".equalsIgnoreCase(this.httpConfiguration.getAuthMethod())) {
                return CompositeHttpConfigurer.combineConfigurers(configurer, new BasicAuthenticationHttpClientConfigurer(this.httpConfiguration.getAuthUsername(),
                this.httpConfiguration.getAuthPassword(), this.httpConfiguration.getAuthDomain(), this.httpConfiguration.getAuthHost()));
            }
        }

        return configurer;
    }

    private HttpClientConfigurer configureHttpProxy(Map<String, Object> parameters, HttpClientConfigurer configurer, boolean secure) throws Exception {
        String proxyAuthScheme = getParameter(parameters, "proxyAuthScheme", String.class);
        if (proxyAuthScheme == null) {
            // fallback and use either http or https depending on secure
            proxyAuthScheme = secure ? "https" : "http";
        }
        String proxyAuthHost = getParameter(parameters, "proxyAuthHost", String.class);
        Integer proxyAuthPort = getParameter(parameters, "proxyAuthPort", Integer.class);

        if (proxyAuthHost != null && proxyAuthPort != null) {
            String proxyAuthUsername = getParameter(parameters, "proxyAuthUsername", String.class);
            String proxyAuthPassword = getParameter(parameters, "proxyAuthPassword", String.class);
            String proxyAuthDomain = getParameter(parameters, "proxyAuthDomain", String.class);
            String proxyAuthNtHost = getParameter(parameters, "proxyAuthNtHost", String.class);

            if (proxyAuthUsername != null && proxyAuthPassword != null) {
                return CompositeHttpConfigurer.combineConfigurers(
                    configurer, new ProxyHttpClientConfigurer(proxyAuthHost, proxyAuthPort, proxyAuthScheme, proxyAuthUsername, proxyAuthPassword, proxyAuthDomain, proxyAuthNtHost));
            } else {
                return CompositeHttpConfigurer.combineConfigurers(configurer, new ProxyHttpClientConfigurer(proxyAuthHost, proxyAuthPort, proxyAuthScheme));
            }
        }

        return configurer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Map<String, Object> httpClientParameters = new HashMap<>(parameters);
        final Map<String, Object> httpClientOptions = new HashMap<>();

        // timeout values can be configured on both component and endpoint level, where endpoint take priority
        int val = getAndRemoveParameter(parameters, "connectionRequestTimeout", int.class, connectionRequestTimeout);
        if (val != -1) {
            httpClientOptions.put("connectionRequestTimeout", val);
        }
        val = getAndRemoveParameter(parameters, "connectTimeout", int.class, connectTimeout);
        if (val != -1) {
            httpClientOptions.put("connectTimeout", val);
        }
        val = getAndRemoveParameter(parameters, "socketTimeout", int.class, socketTimeout);
        if (val != -1) {
            httpClientOptions.put("socketTimeout", val);
        }

        final HttpClientBuilder clientBuilder = createHttpClientBuilder(uri, parameters, httpClientOptions);

        HttpBinding httpBinding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        HttpContext httpContext = resolveAndRemoveReferenceParameter(parameters, "httpContext", HttpContext.class);

        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        if (sslContextParameters == null) {
            sslContextParameters = getSslContextParameters();
        }
        if (sslContextParameters == null) {
            sslContextParameters = retrieveGlobalSslContextParameters();
        }

        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);

        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        boolean secure = HttpHelper.isSecureConnection(uri) || sslContextParameters != null;

        // need to set scheme on address uri depending on if its secure or not
        String addressUri = (secure ? "https://" : "http://") + remaining;

        addressUri = UnsafeUriCharactersEncoder.encodeHttpURI(addressUri);
        URI uriHttpUriAddress = new URI(addressUri);

        // validate http uri that end-user did not duplicate the http part that can be a common error
        int pos = uri.indexOf("//");
        if (pos != -1) {
            String part = uri.substring(pos + 2);
            if (part.startsWith("http:") || part.startsWith("https:")) {
                throw new ResolveEndpointFailedException(uri,
                        "The uri part is not configured correctly. You have duplicated the http(s) protocol.");
            }
        }

        // create the configurer to use for this endpoint
        HttpClientConfigurer configurer = createHttpClientConfigurer(parameters, secure);
        URI endpointUri = URISupport.createRemainingURI(uriHttpUriAddress, httpClientParameters);

        // the endpoint uri should use the component name as scheme, so we need to re-create it once more
        String scheme = StringHelper.before(uri, "://");
        endpointUri = URISupport.createRemainingURI(
                new URI(scheme,
                        endpointUri.getUserInfo(),
                        endpointUri.getHost(),
                        endpointUri.getPort(),
                        endpointUri.getPath(),
                        endpointUri.getQuery(),
                        endpointUri.getFragment()),
                httpClientParameters);

        // create the endpoint and set the http uri to be null
        String endpointUriString = endpointUri.toString();

        LOG.debug("Creating endpoint uri {}", endpointUriString);
        final HttpClientConnectionManager localConnectionManager = createConnectionManager(parameters, sslContextParameters);
        HttpEndpoint endpoint = new HttpEndpoint(endpointUriString, this, clientBuilder, localConnectionManager, configurer);

        // configure the endpoint with the common configuration from the component
        if (getHttpConfiguration() != null) {
            Map<String, Object> properties = new HashMap<>();
            BeanIntrospection beanIntrospection = getCamelContext().adapt(ExtendedCamelContext.class).getBeanIntrospection();
            beanIntrospection.getProperties(getHttpConfiguration(), properties, null);
            setProperties(endpoint, properties);
        }

        // configure the endpoint
        setProperties(endpoint, parameters);

        // we can not change the port of an URI, we must create a new one with an explicit port value
        URI httpUri = URISupport.createRemainingURI(
                new URI(uriHttpUriAddress.getScheme(),
                        uriHttpUriAddress.getUserInfo(),
                        uriHttpUriAddress.getHost(),
                        uriHttpUriAddress.getPort(),
                        uriHttpUriAddress.getPath(),
                        uriHttpUriAddress.getQuery(),
                        uriHttpUriAddress.getFragment()),
                        parameters);

        endpoint.setHttpUri(httpUri);

        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            setEndpointHeaderFilterStrategy(endpoint);
        }
        endpoint.setHttpBinding(getHttpBinding());
        if (httpBinding != null) {
            endpoint.setHttpBinding(httpBinding);
        }
        if (httpMethodRestrict != null) {
            endpoint.setHttpMethodRestrict(httpMethodRestrict);
        }
        endpoint.setHttpContext(getHttpContext());
        if (httpContext != null) {
            endpoint.setHttpContext(httpContext);
        }
        if (endpoint.getCookieStore() == null) {
            endpoint.setCookieStore(getCookieStore());
        }
        endpoint.setHttpClientOptions(httpClientOptions);

        return endpoint;
    }

    protected HttpClientConnectionManager createConnectionManager(final Map<String, Object> parameters,
            final SSLContextParameters sslContextParameters) throws GeneralSecurityException, IOException {
        if (clientConnectionManager != null) {
            return clientConnectionManager;
        }

        final HostnameVerifier resolvedHostnameVerifier = resolveAndRemoveReferenceParameter(parameters, "x509HostnameVerifier", HostnameVerifier.class);
        final HostnameVerifier hostnameVerifier = Optional.ofNullable(resolvedHostnameVerifier).orElse(x509HostnameVerifier);

        // need to check the parameters of maxTotalConnections and connectionsPerRoute
        final int maxTotalConnections = getAndRemoveParameter(parameters, "maxTotalConnections", int.class, 0);
        final int connectionsPerRoute = getAndRemoveParameter(parameters, "connectionsPerRoute", int.class, 0);

        final Registry<ConnectionSocketFactory> connectionRegistry = createConnectionRegistry(hostnameVerifier, sslContextParameters);

        return createConnectionManager(connectionRegistry, maxTotalConnections, connectionsPerRoute);
    }

    protected HttpClientBuilder createHttpClientBuilder(final String uri, final Map<String, Object> parameters,
            final Map<String, Object> httpClientOptions) throws Exception {
        // http client can be configured from URI options
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        // allow the builder pattern
        httpClientOptions.putAll(PropertiesHelper.extractProperties(parameters, "httpClient."));
        PropertyBindingSupport.bindProperties(getCamelContext(), clientBuilder, httpClientOptions);
        // set the Request configure this way and allow the builder pattern
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        PropertyBindingSupport.bindProperties(getCamelContext(), requestConfigBuilder, httpClientOptions);
        clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());

        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, httpClientOptions, null);

        return clientBuilder;
    }

    protected Registry<ConnectionSocketFactory> createConnectionRegistry(HostnameVerifier x509HostnameVerifier, SSLContextParameters sslContextParams)
        throws GeneralSecurityException, IOException {
        // create the default connection registry to use
        RegistryBuilder<ConnectionSocketFactory> builder = RegistryBuilder.<ConnectionSocketFactory>create();
        builder.register("http", PlainConnectionSocketFactory.getSocketFactory());
        if (sslContextParams != null) {
            builder.register("https", new SSLConnectionSocketFactory(sslContextParams.createSSLContext(getCamelContext()), x509HostnameVerifier));
        } else {
            builder.register("https", new SSLConnectionSocketFactory(SSLContexts.createDefault(), x509HostnameVerifier));
        }
        return builder.build();
    }

    protected HttpClientConnectionManager createConnectionManager(Registry<ConnectionSocketFactory> registry) {
        return createConnectionManager(registry, 0, 0);
    }

    protected HttpClientConnectionManager createConnectionManager(Registry<ConnectionSocketFactory> registry, int maxTotalConnections, int connectionsPerRoute) {
        // setup the connection live time
        PoolingHttpClientConnectionManager answer =
            new PoolingHttpClientConnectionManager(registry, null, null, null, getConnectionTimeToLive(), TimeUnit.MILLISECONDS);
        int localMaxTotalConnections = maxTotalConnections;
        if (localMaxTotalConnections == 0) {
            localMaxTotalConnections = getMaxTotalConnections();
        }
        if (localMaxTotalConnections > 0) {
            answer.setMaxTotal(localMaxTotalConnections);
        }
        int localConnectionsPerRoute = connectionsPerRoute;
        if (localConnectionsPerRoute == 0) {
            localConnectionsPerRoute = getConnectionsPerRoute();
        }
        if (localConnectionsPerRoute > 0) {
            answer.setDefaultMaxPerRoute(localConnectionsPerRoute);
        }
        LOG.info("Created ClientConnectionManager {}", answer);

        return answer;
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
    }


    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // get the endpoint
        String url = host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        RestConfiguration config = configuration;
        if (config == null) {
            config = CamelContextHelper.getRestConfiguration(getCamelContext(), "http");
        }

        Map<String, Object> map = new HashMap<>();
        // build query string, and append any endpoint configuration properties
        if (config.getProducerComponent() == null || config.getProducerComponent().equals("http")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        // get the endpoint
        String query = URISupport.createQueryString(map);
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }

        parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<String, Object>();

        // there are cases where we might end up here without component being created beforehand
        // we need to abide by the component properties specified in the parameters when creating
        // the component, one such case is when we switch from "http" to "https" component name
        RestProducerFactoryHelper.setupComponentFor(url, camelContext, (Map<String, Object>) parameters.remove("component"));

        HttpEndpoint endpoint = camelContext.getEndpoint(url, HttpEndpoint.class);
        setProperties(endpoint, parameters);
        String path = uriTemplate != null ? uriTemplate : basePath;
        endpoint.setHeaderFilterStrategy(new HttpRestHeaderFilterStrategy(path, queryParameters));

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return endpoint.createProducer();
    }

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
    }

    /**
     * To use the custom HttpClientConfigurer to perform configuration of the HttpClient that will be used.
     */
    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
    }

    public HttpClientConnectionManager getClientConnectionManager() {
        return clientConnectionManager;
    }

    /**
     * To use a custom and shared HttpClientConnectionManager to manage connections.
     * If this has been configured then this is always used for all endpoints created by this component.
     */
    public void setClientConnectionManager(HttpClientConnectionManager clientConnectionManager) {
        this.clientConnectionManager = clientConnectionManager;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    /**
     * To use a custom org.apache.http.protocol.HttpContext when executing requests.
     */
    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters.
     * Important: Only one instance of org.apache.camel.support.jsse.SSLContextParameters is supported per HttpComponent.
     * If you need to use 2 or more different instances, you need to define a new HttpComponent per instance you need.
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

    public HostnameVerifier getX509HostnameVerifier() {
        return x509HostnameVerifier;
    }

    /**
     * To use a custom X509HostnameVerifier such as DefaultHostnameVerifier or NoopHostnameVerifier.
     */
    public void setX509HostnameVerifier(HostnameVerifier x509HostnameVerifier) {
        this.x509HostnameVerifier = x509HostnameVerifier;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    /**
     * The maximum number of connections.
     */
    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public int getConnectionsPerRoute() {
        return connectionsPerRoute;
    }

    /**
     * The maximum number of connections per route.
     */
    public void setConnectionsPerRoute(int connectionsPerRoute) {
        this.connectionsPerRoute = connectionsPerRoute;
    }

    public long getConnectionTimeToLive() {
        return connectionTimeToLive;
    }

    /**
     * The time for connection to live, the time unit is millisecond, the default value is always keep alive.
     */
    public void setConnectionTimeToLive(long connectionTimeToLive) {
        this.connectionTimeToLive = connectionTimeToLive;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    /**
     * To use a custom org.apache.http.client.CookieStore.
     * By default the org.apache.http.impl.client.BasicCookieStore is used which is an in-memory only cookie store.
     * Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie
     * shouldn't be stored as we are just bridging (eg acting as a proxy).
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    /**
     * The timeout in milliseconds used when requesting a connection
     * from the connection manager. A timeout value of zero is interpreted
     * as an infinite timeout.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * A negative value is interpreted as undefined (system default).
     * </p>
     * <p>
     * Default: -1
     * </p>
     */
    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Determines the timeout in milliseconds until a connection is established.
     * A timeout value of zero is interpreted as an infinite timeout.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * A negative value is interpreted as undefined (system default).
     * </p>
     * <p>
     * Default: -1
     * </p>
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Defines the socket timeout (SO_TIMEOUT) in milliseconds,
     * which is the timeout for waiting for data  or, put differently,
     * a maximum period inactivity between two consecutive data packets).
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * A negative value is interpreted as undefined (system default).
     * </p>
     * <p>
     * Default: -1
     * </p>
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        // shutdown connection manager
        if (clientConnectionManager != null) {
            LOG.info("Shutting down ClientConnectionManager: {}", clientConnectionManager);
            clientConnectionManager.shutdown();
            clientConnectionManager = null;
        }

        super.doStop();
    }

    public ComponentVerifierExtension getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
