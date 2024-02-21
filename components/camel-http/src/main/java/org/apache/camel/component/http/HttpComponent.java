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

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
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
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.RestProducerFactoryHelper;
import org.apache.camel.support.http.HttpUtil;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the HTTP Component
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("http,https")
public class HttpComponent extends HttpCommonComponent implements RestProducerFactory, SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(HttpComponent.class);

    @Metadata(label = "advanced",
              description = "To use the custom HttpClientConfigurer to perform configuration of the HttpClient that will be used.")
    protected HttpClientConfigurer httpClientConfigurer;
    @Metadata(label = "advanced", description = "To use a custom and shared HttpClientConnectionManager to manage connections."
                                                + " If this has been configured then this is always used for all endpoints created by this component.")
    protected HttpClientConnectionManager clientConnectionManager;
    @Metadata(label = "advanced",
              description = "To use a custom org.apache.hc.core5.http.protocol.HttpContext when executing requests.")
    protected HttpContext httpContext;
    @Metadata(label = "security", description = "To configure security using SSLContextParameters."
                                                + " Important: Only one instance of org.apache.camel.support.jsse.SSLContextParameters is supported per HttpComponent."
                                                + " If you need to use 2 or more different instances, you need to define a new HttpComponent per instance you need.")
    protected SSLContextParameters sslContextParameters;
    @Metadata(label = "security",
              description = "To use a custom X509HostnameVerifier such as DefaultHostnameVerifier or NoopHostnameVerifier.")
    protected HostnameVerifier x509HostnameVerifier = new DefaultHostnameVerifier();
    @Metadata(label = "producer,advanced", description = "To use a custom org.apache.hc.client5.http.cookie.CookieStore."
                                                         + " By default the org.apache.hc.client5.http.cookie.BasicCookieStore is used which is an in-memory only cookie store."
                                                         + " Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie"
                                                         + " shouldn't be stored as we are just bridging (eg acting as a proxy).")
    protected CookieStore cookieStore;

    // timeout
    @Metadata(label = "timeout", defaultValue = "3 minutes",
              description = "Returns the connection lease request timeout used when requesting"
                            + " a connection from the connection manager."
                            + " A timeout value of zero is interpreted as a disabled timeout.",
              javaType = "org.apache.hc.core5.util.Timeout")
    protected Timeout connectionRequestTimeout = Timeout.ofMinutes(3);
    @Metadata(label = "timeout", defaultValue = "3 minutes",
              description = "Determines the timeout until a new connection is fully established."
                            + " A timeout value of zero is interpreted as an infinite timeout.",
              javaType = "org.apache.hc.core5.util.Timeout")
    protected Timeout connectTimeout = Timeout.ofMinutes(3);
    @Metadata(label = "timeout", defaultValue = "3 minutes",
              description = "Determines the default socket timeout value for blocking I/O operations.",
              javaType = "org.apache.hc.core5.util.Timeout")
    protected Timeout soTimeout = Timeout.ofMinutes(3);
    @Metadata(label = "timeout", defaultValue = "0",
              description = "Determines the timeout until arrival of a response from the opposite"
                            + " endpoint. A timeout value of zero is interpreted as an infinite timeout."
                            + " Please note that response timeout may be unsupported by HTTP transports "
                            + "with message multiplexing.",
              javaType = "org.apache.hc.core5.util.Timeout")
    protected Timeout responseTimeout = Timeout.ofMilliseconds(0);

    // proxy
    @Metadata(label = "producer,proxy", enums = "http,https", description = "Proxy authentication protocol scheme")
    protected String proxyAuthScheme;
    @Metadata(label = "producer,proxy", enums = "Basic,Digest,NTLM", description = "Proxy authentication method to use")
    protected String proxyAuthMethod;
    @Metadata(label = "producer,proxy", secret = true, description = "Proxy authentication username")
    protected String proxyAuthUsername;
    @Metadata(label = "producer,proxy", secret = true, description = "Proxy authentication password")
    protected String proxyAuthPassword;
    @Metadata(label = "producer,proxy", description = "Proxy authentication host")
    protected String proxyAuthHost;
    @Metadata(label = "producer,proxy", description = "Proxy authentication port")
    protected Integer proxyAuthPort;
    @Metadata(label = "producer,proxy", description = "Proxy authentication domain to use")
    protected String proxyAuthDomain;
    @Metadata(label = "producer,proxy", description = "Proxy authentication domain (workstation name) to use with NTML")
    protected String proxyAuthNtHost;

    // options to the default created http connection manager
    @Metadata(label = "advanced", defaultValue = "200", description = "The maximum number of connections.")
    protected int maxTotalConnections = 200;
    @Metadata(label = "advanced", defaultValue = "20", description = "The maximum number of connections per route.")
    protected int connectionsPerRoute = 20;
    // It's MILLISECONDS, the default value is always keep alive
    @Metadata(label = "advanced",
              description = "The time for connection to live, the time unit is millisecond, the default value is always keep alive.")
    protected long connectionTimeToLive = -1;
    @Metadata(label = "security", defaultValue = "false", description = "Enable usage of global SSL context parameters.")
    protected boolean useGlobalSslContextParameters;
    @Metadata(label = "producer,advanced", defaultValue = "8192",
              description = "This threshold in bytes controls whether the response payload"
                            + " should be stored in memory as a byte array or be streaming based. Set this to -1 to always use streaming mode.")
    protected int responsePayloadStreamingThreshold = 8192;
    @Metadata(label = "advanced", description = "Disables automatic redirect handling")
    protected boolean redirectHandlingDisabled;
    @Metadata(label = "advanced", description = "Disables automatic request recovery and re-execution")
    protected boolean automaticRetriesDisabled;
    @Metadata(label = "advanced", description = "Disables automatic content decompression")
    protected boolean contentCompressionDisabled;
    @Metadata(label = "advanced", description = "Disables state (cookie) management")
    protected boolean cookieManagementDisabled;
    @Metadata(label = "advanced", description = "Disables authentication scheme caching")
    protected boolean authCachingDisabled;
    @Metadata(label = "advanced", description = "Disables connection state tracking")
    protected boolean connectionStateDisabled;
    @Metadata(label = "advanced",
              description = "Disables the default user agent set by this builder if none has been provided by the user")
    protected boolean defaultUserAgentDisabled;
    @Metadata(label = "producer",
              description = "Whether to skip mapping all the Camel headers as HTTP request headers."
                            + " If there are no data from Camel headers needed to be included in the HTTP request then this can avoid"
                            + " parsing overhead with many object allocations for the JVM garbage collector.")
    protected boolean skipRequestHeaders;
    @Metadata(label = "producer",
              description = "Whether to skip mapping all the HTTP response headers to Camel headers."
                            + " If there are no data needed from HTTP headers then this can avoid parsing overhead"
                            + " with many object allocations for the JVM garbage collector.")
    protected boolean skipResponseHeaders;
    @Metadata(label = "producer,advanced",
              defaultValue = "true",
              description = "If this option is true then IN exchange headers will be copied to OUT exchange headers according to copy strategy."
                            + " Setting this to false, allows to only include the headers from the HTTP response (not propagating IN headers).")
    protected boolean copyHeaders = true;
    @Metadata(label = "producer,advanced", defaultValue = "false",
              description = "Whether to the HTTP request should follow redirects."
                            + " By default the HTTP request does not follow redirects ")
    protected boolean followRedirects;
    @UriParam(label = "producer,advanced", description = "To set a custom HTTP User-Agent request header")
    protected String userAgent;

    public HttpComponent() {
        registerExtension(HttpComponentVerifierExtension::new);
    }

    /**
     * Creates the HttpClientConfigurer based on the given parameters
     *
     * @param  parameters the map of parameters
     * @param  secure     whether the endpoint is secure (eg https)
     * @return            the configurer
     * @throws Exception  is thrown if error creating configurer
     */
    protected HttpClientConfigurer createHttpClientConfigurer(Map<String, Object> parameters, boolean secure) throws Exception {
        // prefer to use endpoint configured over component configured
        HttpClientConfigurer configurer
                = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurer", HttpClientConfigurer.class);
        if (configurer == null) {
            // fallback to component configured
            configurer = getHttpClientConfigurer();
        }
        HttpCredentialsHelper credentialsProvider = new HttpCredentialsHelper();
        configurer = configureBasicAuthentication(parameters, configurer, credentialsProvider);
        configurer = configureHttpProxy(parameters, configurer, secure, credentialsProvider);
        configurer = configureOAuth2Authentication(parameters, configurer);

        return configurer;
    }

    private HttpClientConfigurer configureOAuth2Authentication(
            Map<String, Object> parameters, HttpClientConfigurer configurer) {

        String clientId = getParameter(parameters, "oauth2ClientId", String.class);
        String clientSecret = getParameter(parameters, "oauth2ClientSecret", String.class);
        String tokenEndpoint = getParameter(parameters, "oauth2TokenEndpoint", String.class);

        if (clientId != null && clientSecret != null && tokenEndpoint != null) {
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new OAuth2ClientConfigurer(clientId, clientSecret, tokenEndpoint));
        }
        return configurer;
    }

    private HttpClientConfigurer configureBasicAuthentication(
            Map<String, Object> parameters, HttpClientConfigurer configurer,
            HttpCredentialsHelper credentialsProvider) {
        String authUsername = getParameter(parameters, "authUsername", String.class);
        String authPassword = getParameter(parameters, "authPassword", String.class);

        if (authUsername != null && authPassword != null) {
            String authDomain = getParameter(parameters, "authDomain", String.class);
            String authHost = getParameter(parameters, "authHost", String.class);

            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new BasicAuthenticationHttpClientConfigurer(
                            authUsername, authPassword, authDomain, authHost, credentialsProvider));
        } else if (this.httpConfiguration != null) {
            if ("basic".equalsIgnoreCase(this.httpConfiguration.getAuthMethod())) {
                return CompositeHttpConfigurer.combineConfigurers(configurer,
                        new BasicAuthenticationHttpClientConfigurer(
                                this.httpConfiguration.getAuthUsername(),
                                this.httpConfiguration.getAuthPassword(), this.httpConfiguration.getAuthDomain(),
                                this.httpConfiguration.getAuthHost(), credentialsProvider));
            }
        }

        return configurer;
    }

    private HttpClientConfigurer configureHttpProxy(
            Map<String, Object> parameters, HttpClientConfigurer configurer, boolean secure,
            HttpCredentialsHelper credentialsProvider) {
        String proxyAuthScheme = getParameter(parameters, "proxyAuthScheme", String.class, getProxyAuthScheme());
        if (proxyAuthScheme == null) {
            // fallback and use either http or https depending on secure
            proxyAuthScheme = secure ? "https" : "http";
        }
        String proxyAuthHost = getParameter(parameters, "proxyAuthHost", String.class, getProxyAuthHost());
        Integer proxyAuthPort = getParameter(parameters, "proxyAuthPort", Integer.class, getProxyAuthPort());
        // fallback to alternative option name
        if (proxyAuthHost == null) {
            proxyAuthHost = getParameter(parameters, "proxyHost", String.class);
        }
        if (proxyAuthPort == null) {
            proxyAuthPort = getParameter(parameters, "proxyPort", Integer.class);
        }

        if (proxyAuthHost != null && proxyAuthPort != null) {
            String proxyAuthUsername = getParameter(parameters, "proxyAuthUsername", String.class, getProxyAuthUsername());
            String proxyAuthPassword = getParameter(parameters, "proxyAuthPassword", String.class, getProxyAuthPassword());
            String proxyAuthDomain = getParameter(parameters, "proxyAuthDomain", String.class, getProxyAuthDomain());
            String proxyAuthNtHost = getParameter(parameters, "proxyAuthNtHost", String.class, getProxyAuthNtHost());

            LOG.debug("Configuring HTTP client to use HTTP proxy {}:{}", proxyAuthHost, proxyAuthPort);

            if (proxyAuthUsername != null && proxyAuthPassword != null) {
                return CompositeHttpConfigurer.combineConfigurers(
                        configurer,
                        new ProxyHttpClientConfigurer(
                                proxyAuthHost, proxyAuthPort, proxyAuthScheme, proxyAuthUsername, proxyAuthPassword,
                                proxyAuthDomain, proxyAuthNtHost, credentialsProvider));
            } else {
                return CompositeHttpConfigurer.combineConfigurers(configurer,
                        new ProxyHttpClientConfigurer(proxyAuthHost, proxyAuthPort, proxyAuthScheme));
            }
        }

        return configurer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Map<String, Object> httpClientParameters = new HashMap<>(parameters);
        final Map<String, Object> httpClientOptions = new HashMap<>();

        // timeout values can be configured on both component and endpoint level, where endpoint take priority
        Timeout valConnectionRequestTimeout
                = getAndRemoveParameter(parameters, "connectionRequestTimeout", Timeout.class, connectionRequestTimeout);
        if (!Timeout.ofMinutes(3).equals(valConnectionRequestTimeout)) {
            httpClientOptions.put("connectionRequestTimeout", valConnectionRequestTimeout);
        }
        Timeout valResponseTimeout = getAndRemoveParameter(parameters, "responseTimeout", Timeout.class, responseTimeout);
        if (!Timeout.ofMilliseconds(0).equals(valResponseTimeout)) {
            httpClientOptions.put("responseTimeout", valResponseTimeout);
        }
        Timeout valConnectTimeout = getAndRemoveParameter(parameters, "connectTimeout", Timeout.class, connectTimeout);
        if (!Timeout.ofMinutes(3).equals(valConnectTimeout)) {
            httpClientOptions.put("connectTimeout", valConnectTimeout);
        }
        final Map<String, Object> httpConnectionOptions = new HashMap<>();
        Timeout valSoTimeout = getAndRemoveParameter(parameters, "soTimeout", Timeout.class, soTimeout);
        if (!Timeout.ofMinutes(3).equals(valSoTimeout)) {
            httpConnectionOptions.put("soTimeout", valSoTimeout);
        }

        HttpBinding httpBinding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        HttpContext httpContext = resolveAndRemoveReferenceParameter(parameters, "httpContext", HttpContext.class);

        SSLContextParameters sslContextParameters
                = resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        if (sslContextParameters == null) {
            sslContextParameters = getSslContextParameters();
        }
        if (sslContextParameters == null) {
            // only secure (https) should use global SSL
            boolean secure = HttpHelper.isSecureConnection(uri);
            if (secure) {
                sslContextParameters = retrieveGlobalSslContextParameters();
            }
        }

        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        boolean muteException = getAndRemoveParameter(parameters, "muteException", boolean.class, isMuteException());

        HeaderFilterStrategy headerFilterStrategy
                = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        // the actual protocol if present in the remainder part should take precedence
        String secureProtocol = uri;
        if (remaining.startsWith("http:") || remaining.startsWith("https:")) {
            secureProtocol = remaining;
        }
        boolean secure = HttpHelper.isSecureConnection(secureProtocol) || sslContextParameters != null;

        // remaining part should be without protocol as that was how this component was originally created
        remaining = org.apache.camel.component.http.HttpUtil.removeHttpOrHttpsProtocol(remaining);

        // need to set scheme on address uri depending on if its secure or not
        String addressUri = (secure ? "https://" : "http://") + remaining;

        addressUri = UnsafeUriCharactersEncoder.encodeHttpURI(addressUri);
        URI uriHttpUriAddress = new URI(addressUri);

        // the endpoint uri should use the component name as scheme, so we need to re-create it once more
        String scheme = StringHelper.before(uri, "://");

        // uri part should be without protocol as that was how this component was originally created
        uri = org.apache.camel.component.http.HttpUtil.removeHttpOrHttpsProtocol(uri);

        // create the configurer to use for this endpoint
        HttpClientConfigurer configurer = createHttpClientConfigurer(parameters, secure);
        URI endpointUri = URISupport.createRemainingURI(uriHttpUriAddress, httpClientParameters);

        endpointUri = URISupport.createRemainingURI(
                new URI(
                        scheme,
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
        final HttpClientConnectionManager localConnectionManager
                = createConnectionManager(parameters, sslContextParameters, httpConnectionOptions);
        final HttpClientBuilder clientBuilder = createHttpClientBuilder(uri, parameters, httpClientOptions);
        HttpEndpoint endpoint = new HttpEndpoint(endpointUriString, this, clientBuilder, localConnectionManager, configurer);
        endpoint.setResponseTimeout(valResponseTimeout);
        endpoint.setSoTimeout(valSoTimeout);
        endpoint.setConnectTimeout(valConnectTimeout);
        endpoint.setConnectionRequestTimeout(valConnectionRequestTimeout);
        endpoint.setCopyHeaders(copyHeaders);
        endpoint.setSkipRequestHeaders(skipRequestHeaders);
        endpoint.setSkipResponseHeaders(skipResponseHeaders);
        endpoint.setUserAgent(userAgent);
        endpoint.setMuteException(muteException);

        // configure the endpoint with the common configuration from the component
        if (getHttpConfiguration() != null) {
            Map<String, Object> properties = new HashMap<>();
            BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(getCamelContext());
            beanIntrospection.getProperties(getHttpConfiguration(), properties, null);
            setProperties(endpoint, properties);
        }

        // configure the endpoint
        setProperties(endpoint, parameters);

        // we can not change the port of an URI, we must create a new one with an explicit port value
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
        endpoint.setHttpConnectionOptions(httpConnectionOptions);
        return endpoint;
    }

    protected HttpClientConnectionManager createConnectionManager(
            final Map<String, Object> parameters,
            final SSLContextParameters sslContextParameters, Map<String, Object> httpConnectionOptions)
            throws GeneralSecurityException, IOException {
        if (clientConnectionManager != null) {
            return clientConnectionManager;
        }

        final HostnameVerifier resolvedHostnameVerifier
                = resolveAndRemoveReferenceParameter(parameters, "x509HostnameVerifier", HostnameVerifier.class);
        final HostnameVerifier hostnameVerifier = Optional.ofNullable(resolvedHostnameVerifier).orElse(x509HostnameVerifier);

        // need to check the parameters of maxTotalConnections and connectionsPerRoute
        final int maxTotalConnections = getAndRemoveParameter(parameters, "maxTotalConnections", int.class, 0);
        final int connectionsPerRoute = getAndRemoveParameter(parameters, "connectionsPerRoute", int.class, 0);
        final boolean useSystemProperties = CamelContextHelper.mandatoryConvertTo(this.getCamelContext(), boolean.class,
                parameters.get("useSystemProperties"));

        final Registry<ConnectionSocketFactory> connectionRegistry
                = createConnectionRegistry(hostnameVerifier, sslContextParameters, useSystemProperties);

        // allow the builder pattern
        httpConnectionOptions.putAll(PropertiesHelper.extractProperties(parameters, "httpConnection."));
        SocketConfig.Builder socketConfigBuilder = SocketConfig.custom();
        PropertyBindingSupport.bindProperties(getCamelContext(), socketConfigBuilder, httpConnectionOptions);

        return createConnectionManager(connectionRegistry, maxTotalConnections, connectionsPerRoute,
                socketConfigBuilder.build());
    }

    protected HttpClientBuilder createHttpClientBuilder(
            final String uri, final Map<String, Object> parameters,
            final Map<String, Object> httpClientOptions) {
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

        // endpoint parameter can override component level
        boolean fr = getParameter(parameters, "followRedirects", Boolean.class, followRedirects);

        if (redirectHandlingDisabled || !fr) {
            clientBuilder.disableRedirectHandling();
        }
        if (automaticRetriesDisabled) {
            clientBuilder.disableAutomaticRetries();
        }
        if (contentCompressionDisabled) {
            clientBuilder.disableContentCompression();
        }
        if (cookieManagementDisabled) {
            clientBuilder.disableCookieManagement();
        }
        if (authCachingDisabled) {
            clientBuilder.disableAuthCaching();
        }
        if (connectionStateDisabled) {
            clientBuilder.disableConnectionState();
        }
        if (defaultUserAgentDisabled) {
            clientBuilder.disableDefaultUserAgent();
        }
        if (fr) {
            clientBuilder.setRedirectStrategy(DefaultRedirectStrategy.INSTANCE);
        }

        return clientBuilder;
    }

    protected Registry<ConnectionSocketFactory> createConnectionRegistry(
            HostnameVerifier x509HostnameVerifier, SSLContextParameters sslContextParams,
            boolean useSystemProperties)
            throws GeneralSecurityException, IOException {
        // create the default connection registry to use
        RegistryBuilder<ConnectionSocketFactory> builder = RegistryBuilder.create();
        builder.register("http", PlainConnectionSocketFactory.getSocketFactory());
        if (sslContextParams != null) {
            builder.register("https",
                    new SSLConnectionSocketFactory(sslContextParams.createSSLContext(getCamelContext()), x509HostnameVerifier));
        } else {
            builder.register("https", new SSLConnectionSocketFactory(
                    useSystemProperties ? SSLContexts.createSystemDefault() : SSLContexts.createDefault(),
                    x509HostnameVerifier));
        }
        return builder.build();
    }

    protected HttpClientConnectionManager createConnectionManager(
            Registry<ConnectionSocketFactory> registry, int maxTotalConnections, int connectionsPerRoute,
            SocketConfig defaultSocketConfig) {
        // setup the connection live time
        PoolingHttpClientConnectionManager answer = new PoolingHttpClientConnectionManager(
                registry, PoolConcurrencyPolicy.STRICT, TimeValue.ofMilliseconds(getConnectionTimeToLive()), null);
        int localMaxTotalConnections = maxTotalConnections;
        if (localMaxTotalConnections == 0) {
            localMaxTotalConnections = getMaxTotalConnections();
        }
        if (localMaxTotalConnections > 0) {
            answer.setMaxTotal(localMaxTotalConnections);
        }
        answer.setDefaultSocketConfig(defaultSocketConfig);
        int localConnectionsPerRoute = connectionsPerRoute;
        if (localConnectionsPerRoute == 0) {
            localConnectionsPerRoute = getConnectionsPerRoute();
        }
        if (localConnectionsPerRoute > 0) {
            answer.setDefaultMaxPerRoute(localConnectionsPerRoute);
        }
        LOG.debug("Created ClientConnectionManager {}", answer);

        return answer;
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
    }

    @SuppressWarnings("unchecked")
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
        String url = host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        RestConfiguration config = configuration;
        if (config == null) {
            config = CamelContextHelper.getRestConfiguration(getCamelContext(), null, "http");
        }

        Map<String, Object> map = new HashMap<>();
        // build query string, and append any endpoint configuration properties
        if (config.getProducerComponent() == null || config.getProducerComponent().equals("http")) {
            // setup endpoint options
            map.put("httpMethod", verb);
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        url = HttpUtil.recreateUrl(map, url);

        parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

        // there are cases where we might end up here without component being created beforehand
        // we need to abide by the component properties specified in the parameters when creating
        // the component, one such case is when we switch from "http" to "https" component name
        RestProducerFactoryHelper.setupComponentFor(url, camelContext, (Map<String, Object>) parameters.remove("component"));

        HttpEndpoint endpoint = (HttpEndpoint) camelContext.getEndpoint(url, parameters);

        String path = uriTemplate != null ? uriTemplate : basePath;

        HeaderFilterStrategy headerFilterStrategy
                = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            endpoint.setHeaderFilterStrategy(new HttpRestHeaderFilterStrategy(path, queryParameters));
        }
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
     * To use a custom and shared HttpClientConnectionManager to manage connections. If this has been configured then
     * this is always used for all endpoints created by this component.
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
     * To configure security using SSLContextParameters. Important: Only one instance of
     * org.apache.camel.support.jsse.SSLContextParameters is supported per HttpComponent. If you need to use 2 or more
     * different instances, you need to define a new HttpComponent per instance you need.
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
     * To use a custom org.apache.http.client.CookieStore. By default the org.apache.http.impl.client.BasicCookieStore
     * is used which is an in-memory only cookie store. Notice if bridgeEndpoint=true then the cookie store is forced to
     * be a noop cookie store as cookie shouldn't be stored as we are just bridging (eg acting as a proxy).
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public Timeout getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    /**
     * Returns the connection lease request timeout used when requesting a connection from the connection manager.
     * <p>
     * A timeout value of zero is interpreted as a disabled timeout.
     * </p>
     * <p>
     * Default: 3 minutes
     * </p>
     */
    public void setConnectionRequestTimeout(Timeout connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Determines the timeout until a new connection is fully established. This may also include transport security
     * negotiation exchanges such as {@code SSL} or {@code TLS} protocol negotiation).
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Default: 3 minutes
     * </p>
     */
    public void setConnectTimeout(Timeout connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Timeout getSoTimeout() {
        return soTimeout;
    }

    /**
     * Determines the default socket timeout value for blocking I/O operations.
     * <p>
     * Default: 3 minutes
     * </p>
     */
    public void setSoTimeout(Timeout soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Timeout getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Determines the timeout until arrival of a response from the opposite endpoint.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Please note that response timeout may be unsupported by HTTP transports with message multiplexing.
     * </p>
     * <p>
     * Default: {@code 0}
     * </p>
     */
    public void setResponseTimeout(Timeout responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public String getProxyAuthScheme() {
        return proxyAuthScheme;
    }

    public void setProxyAuthScheme(String proxyAuthScheme) {
        this.proxyAuthScheme = proxyAuthScheme;
    }

    public String getProxyAuthMethod() {
        return proxyAuthMethod;
    }

    public void setProxyAuthMethod(String proxyAuthMethod) {
        this.proxyAuthMethod = proxyAuthMethod;
    }

    public String getProxyAuthUsername() {
        return proxyAuthUsername;
    }

    public void setProxyAuthUsername(String proxyAuthUsername) {
        this.proxyAuthUsername = proxyAuthUsername;
    }

    public String getProxyAuthPassword() {
        return proxyAuthPassword;
    }

    public void setProxyAuthPassword(String proxyAuthPassword) {
        this.proxyAuthPassword = proxyAuthPassword;
    }

    public String getProxyAuthHost() {
        return proxyAuthHost;
    }

    public void setProxyAuthHost(String proxyAuthHost) {
        this.proxyAuthHost = proxyAuthHost;
    }

    public Integer getProxyAuthPort() {
        return proxyAuthPort;
    }

    public void setProxyAuthPort(Integer proxyAuthPort) {
        this.proxyAuthPort = proxyAuthPort;
    }

    public String getProxyAuthDomain() {
        return proxyAuthDomain;
    }

    public void setProxyAuthDomain(String proxyAuthDomain) {
        this.proxyAuthDomain = proxyAuthDomain;
    }

    public String getProxyAuthNtHost() {
        return proxyAuthNtHost;
    }

    public void setProxyAuthNtHost(String proxyAuthNtHost) {
        this.proxyAuthNtHost = proxyAuthNtHost;
    }

    public int getResponsePayloadStreamingThreshold() {
        return responsePayloadStreamingThreshold;
    }

    public void setResponsePayloadStreamingThreshold(int responsePayloadStreamingThreshold) {
        this.responsePayloadStreamingThreshold = responsePayloadStreamingThreshold;
    }

    public boolean isRedirectHandlingDisabled() {
        return redirectHandlingDisabled;
    }

    public void setRedirectHandlingDisabled(boolean redirectHandlingDisabled) {
        this.redirectHandlingDisabled = redirectHandlingDisabled;
    }

    public boolean isAutomaticRetriesDisabled() {
        return automaticRetriesDisabled;
    }

    public void setAutomaticRetriesDisabled(boolean automaticRetriesDisabled) {
        this.automaticRetriesDisabled = automaticRetriesDisabled;
    }

    public boolean isContentCompressionDisabled() {
        return contentCompressionDisabled;
    }

    public void setContentCompressionDisabled(boolean contentCompressionDisabled) {
        this.contentCompressionDisabled = contentCompressionDisabled;
    }

    public boolean isCookieManagementDisabled() {
        return cookieManagementDisabled;
    }

    public void setCookieManagementDisabled(boolean cookieManagementDisabled) {
        this.cookieManagementDisabled = cookieManagementDisabled;
    }

    public boolean isAuthCachingDisabled() {
        return authCachingDisabled;
    }

    public void setAuthCachingDisabled(boolean authCachingDisabled) {
        this.authCachingDisabled = authCachingDisabled;
    }

    public boolean isConnectionStateDisabled() {
        return connectionStateDisabled;
    }

    public void setConnectionStateDisabled(boolean connectionStateDisabled) {
        this.connectionStateDisabled = connectionStateDisabled;
    }

    public boolean isDefaultUserAgentDisabled() {
        return defaultUserAgentDisabled;
    }

    public void setDefaultUserAgentDisabled(boolean defaultUserAgentDisabled) {
        this.defaultUserAgentDisabled = defaultUserAgentDisabled;
    }

    public boolean isCopyHeaders() {
        return copyHeaders;
    }

    public void setCopyHeaders(boolean copyHeaders) {
        this.copyHeaders = copyHeaders;
    }

    public boolean isSkipRequestHeaders() {
        return skipRequestHeaders;
    }

    public void setSkipRequestHeaders(boolean skipRequestHeaders) {
        this.skipRequestHeaders = skipRequestHeaders;
    }

    public boolean isSkipResponseHeaders() {
        return skipResponseHeaders;
    }

    public void setSkipResponseHeaders(boolean skipResponseHeaders) {
        this.skipResponseHeaders = skipResponseHeaders;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
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
            clientConnectionManager.close();
            clientConnectionManager = null;
        }

        super.doStop();
    }

    public ComponentVerifierExtension getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class)
                .orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
