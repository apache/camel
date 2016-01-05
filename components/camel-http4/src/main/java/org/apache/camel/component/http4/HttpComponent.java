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
package org.apache.camel.component.http4;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.UrlRewrite;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/http4.html">HTTP4
 * Component</a>
 *
 * @version 
 */
public class HttpComponent extends HttpCommonComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HttpComponent.class);

    protected HttpClientConfigurer httpClientConfigurer;
    protected HttpClientConnectionManager clientConnectionManager;
    protected HttpContext httpContext;
    protected SSLContextParameters sslContextParameters;
    protected X509HostnameVerifier x509HostnameVerifier = new BrowserCompatHostnameVerifier();
    protected CookieStore cookieStore;

    // options to the default created http connection manager
    protected int maxTotalConnections = 200;
    protected int connectionsPerRoute = 20;
    // It's MILLISECONDS, the default value is always keep alive
    protected long connectionTimeToLive = -1;

    public HttpComponent() {
        super(HttpEndpoint.class);
    }

    public HttpComponent(Class<? extends HttpEndpoint> endpointClass) {
        super(endpointClass);
    }

    /**
     * Creates the HttpClientConfigurer based on the given parameters
     *
     * @param parameters the map of parameters
     * @param secure whether the endpoint is secure (eg https4)
     * @return the configurer
     * @throws Exception is thrown if error creating configurer
     */
    protected HttpClientConfigurer createHttpClientConfigurer(Map<String, Object> parameters, boolean secure) throws Exception {
        // prefer to use endpoint configured over component configured
        // TODO cmueller: remove the "httpClientConfigurerRef" look up in Camel 3.0
        HttpClientConfigurer configurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurerRef", HttpClientConfigurer.class);
        if (configurer == null) {
            // try without ref
            configurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurer", HttpClientConfigurer.class);
            
            if (configurer == null) {
                // fallback to component configured
                configurer = getHttpClientConfigurer();
            }
        }

        configurer = configureBasicAuthentication(parameters, configurer);
        configurer = configureHttpProxy(parameters, configurer, secure);

        return configurer;
    }

    private HttpClientConfigurer configureBasicAuthentication(Map<String, Object> parameters, HttpClientConfigurer configurer) {
        String authUsername = getAndRemoveParameter(parameters, "authUsername", String.class);
        String authPassword = getAndRemoveParameter(parameters, "authPassword", String.class);

        if (authUsername != null && authPassword != null) {
            String authDomain = getAndRemoveParameter(parameters, "authDomain", String.class);
            String authHost = getAndRemoveParameter(parameters, "authHost", String.class);
            
            return CompositeHttpConfigurer.combineConfigurers(configurer, new BasicAuthenticationHttpClientConfigurer(authUsername, authPassword, authDomain, authHost));
        }
        
        return configurer;
    }

    private HttpClientConfigurer configureHttpProxy(Map<String, Object> parameters, HttpClientConfigurer configurer, boolean secure) throws Exception {
        String proxyAuthScheme = getAndRemoveParameter(parameters, "proxyAuthScheme", String.class);
        if (proxyAuthScheme == null) {
            // fallback and use either http or https depending on secure
            proxyAuthScheme = secure ? "https" : "http";
        }
        String proxyAuthHost = getAndRemoveParameter(parameters, "proxyAuthHost", String.class);
        Integer proxyAuthPort = getAndRemoveParameter(parameters, "proxyAuthPort", Integer.class);
        
        if (proxyAuthHost != null && proxyAuthPort != null) {
            String proxyAuthUsername = getAndRemoveParameter(parameters, "proxyAuthUsername", String.class);
            String proxyAuthPassword = getAndRemoveParameter(parameters, "proxyAuthPassword", String.class);
            String proxyAuthDomain = getAndRemoveParameter(parameters, "proxyAuthDomain", String.class);
            String proxyAuthNtHost = getAndRemoveParameter(parameters, "proxyAuthNtHost", String.class);
            
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
        Map<String, Object> httpClientParameters = new HashMap<String, Object>(parameters);
        // http client can be configured from URI options
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        // allow the builder pattern
        Map<String, Object> httpClientOptions = IntrospectionSupport.extractProperties(parameters, "httpClient.");
        IntrospectionSupport.setProperties(clientBuilder, httpClientOptions);
        // set the Request configure this way and allow the builder pattern
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        IntrospectionSupport.setProperties(requestConfigBuilder, httpClientOptions);
        clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
        
        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, httpClientOptions, null);
        
        // TODO cmueller: remove the "httpBindingRef" look up in Camel 3.0
        HttpBinding httpBinding = resolveAndRemoveReferenceParameter(parameters, "httpBindingRef", HttpBinding.class);
        if (httpBinding == null) {
            httpBinding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        }

        // TODO cmueller: remove the "httpContextRef" look up in Camel 3.0
        HttpContext httpContext = resolveAndRemoveReferenceParameter(parameters, "httpContextRef", HttpContext.class);
        if (httpContext == null) {
            httpContext = resolveAndRemoveReferenceParameter(parameters, "httpContext", HttpContext.class);
        }

        X509HostnameVerifier x509HostnameVerifier = resolveAndRemoveReferenceParameter(parameters, "x509HostnameVerifier", X509HostnameVerifier.class);
        if (x509HostnameVerifier == null) {
            x509HostnameVerifier = getX509HostnameVerifier();
        }
        

        // TODO cmueller: remove the "sslContextParametersRef" look up in Camel 3.0
        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParametersRef", SSLContextParameters.class);
        if (sslContextParameters == null) {
            sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParameters", SSLContextParameters.class);
        }
        if (sslContextParameters == null) {
            sslContextParameters = getSslContextParameters();
        }
        
        String httpMethodRestrict = getAndRemoveParameter(parameters, "httpMethodRestrict", String.class);
        
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        UrlRewrite urlRewrite = resolveAndRemoveReferenceParameter(parameters, "urlRewrite", UrlRewrite.class);

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
        String scheme = ObjectHelper.before(uri, "://");
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
        HttpClientConnectionManager localConnectionManager = clientConnectionManager;
        if (localConnectionManager == null) {
            // need to check the parameters of maxTotalConnections and connectionsPerRoute
            int maxTotalConnections = getAndRemoveParameter(parameters, "maxTotalConnections", int.class, 0);
            int connectionsPerRoute = getAndRemoveParameter(parameters, "connectionsPerRoute", int.class, 0);
            localConnectionManager = createConnectionManager(createConnectionRegistry(x509HostnameVerifier, sslContextParameters), maxTotalConnections, connectionsPerRoute);
        }
        HttpEndpoint endpoint = new HttpEndpoint(endpointUriString, this, clientBuilder, localConnectionManager, configurer);
        if (urlRewrite != null) {
            // let CamelContext deal with the lifecycle of the url rewrite
            // this ensures its being shutdown when Camel shutdown etc.
            getCamelContext().addService(urlRewrite);
            endpoint.setUrlRewrite(urlRewrite);
        }
        // configure the endpoint
        setProperties(endpoint, parameters);

        // determine the portnumber (special case: default portnumber)
        //int port = getPort(uriHttpUriAddress);

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
        endpoint.setBinding(getHttpBinding());
        if (httpBinding != null) {
            endpoint.setBinding(httpBinding);
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
    
    protected Registry<ConnectionSocketFactory> createConnectionRegistry(X509HostnameVerifier x509HostnameVerifier, SSLContextParameters sslContextParams)
        throws GeneralSecurityException, IOException {
        // create the default connection registry to use
        RegistryBuilder<ConnectionSocketFactory> builder = RegistryBuilder.<ConnectionSocketFactory>create();
        builder.register("http", PlainConnectionSocketFactory.getSocketFactory());
        builder.register("http4", PlainConnectionSocketFactory.getSocketFactory());
        if (sslContextParams != null) {
            builder.register("https", new SSLConnectionSocketFactory(sslContextParams.createSSLContext(), x509HostnameVerifier));
            builder.register("https4", new SSLConnectionSocketFactory(sslContextParams.createSSLContext(), x509HostnameVerifier));
        } else {
            builder.register("https4", new SSLConnectionSocketFactory(SSLContexts.createDefault(), x509HostnameVerifier));
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
        LOG.info("Created ClientConnectionManager " + answer);

        return answer;
    }
    

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
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
     * To use a custom HttpClientConnectionManager to manage connections
     */
    public void setClientConnectionManager(HttpClientConnectionManager clientConnectionManager) {
        this.clientConnectionManager = clientConnectionManager;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setHttpBinding(HttpBinding httpBinding) {
        // need to override and call super for component docs
        super.setHttpBinding(httpBinding);
    }

    /**
     * To use the shared HttpConfiguration as base configuration.
     */
    @Override
    public void setHttpConfiguration(HttpConfiguration httpConfiguration) {
        // need to override and call super for component docs
        super.setHttpConfiguration(httpConfiguration);
    }

    /**
     * Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    @Override
    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
        // need to override and call super for component docs
        super.setAllowJavaSerializedObject(allowJavaSerializedObject);
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
     * Important: Only one instance of org.apache.camel.util.jsse.SSLContextParameters is supported per HttpComponent.
     * If you need to use 2 or more different instances, you need to define a new HttpComponent per instance you need.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public X509HostnameVerifier getX509HostnameVerifier() {
        return x509HostnameVerifier;
    }

    /**
     * To use a custom X509HostnameVerifier such as org.apache.http.conn.ssl.StrictHostnameVerifier
     * or org.apache.http.conn.ssl.AllowAllHostnameVerifier.
     */
    public void setX509HostnameVerifier(X509HostnameVerifier x509HostnameVerifier) {
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

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        // shutdown connection manager
        if (clientConnectionManager != null) {
            LOG.info("Shutting down ClientConnectionManager: " + clientConnectionManager);
            clientConnectionManager.shutdown();
            clientConnectionManager = null;
        }
        
        super.doStop();
    }
    
}
