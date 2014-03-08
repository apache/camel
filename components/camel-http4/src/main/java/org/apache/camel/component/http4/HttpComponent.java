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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.http4.helper.HttpHelper;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.auth.params.AuthParamBean;
import org.apache.http.client.CookieStore;
import org.apache.http.client.params.ClientParamBean;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRouteParamBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.cookie.params.CookieSpecParamBean;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParamBean;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/http4.html">HTTP4
 * Component</a>
 *
 * @version 
 */
public class HttpComponent extends HeaderFilterStrategyComponent {
    private static final Logger LOG = LoggerFactory.getLogger(HttpComponent.class);

    protected HttpClientConfigurer httpClientConfigurer;
    protected ClientConnectionManager clientConnectionManager;
    protected HttpBinding httpBinding;
    protected HttpContext httpContext;
    protected SSLContextParameters sslContextParameters;
    protected X509HostnameVerifier x509HostnameVerifier = new BrowserCompatHostnameVerifier();
    protected CookieStore cookieStore;

    // options to the default created http connection manager
    protected int maxTotalConnections = 200;
    protected int connectionsPerRoute = 20;
    // It's MILLISECONDS, the default value is always keep alive
    protected long connectionTimeToLive = -1;

    private volatile SSLContextParameters usedSslContextParams;

    /**
     * Connects the URL specified on the endpoint to the specified processor.
     *
     * @param consumer the consumer
     * @throws Exception can be thrown
     */
    public void connect(HttpConsumer consumer) throws Exception {
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified processor.
     *
     * @param consumer the consumer
     * @throws Exception can be thrown
     */
    public void disconnect(HttpConsumer consumer) throws Exception {
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
            boolean secureProxy = HttpHelper.isSecureConnection(proxyAuthScheme);

            // register scheme for proxy
            registerPort(secureProxy, x509HostnameVerifier, proxyAuthPort, sslContextParameters);

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
        HttpParams clientParams = configureHttpParams(parameters);
        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, parameters, "httpClient.");
        
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
        String addressUri = remaining.startsWith("http") ? remaining : null;
        if (addressUri == null) {
            if (secure) {
                addressUri = "https://" + remaining;
            } else {
                addressUri = "http://" + remaining;
            }
        }
        addressUri = UnsafeUriCharactersEncoder.encode(addressUri);
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
        HttpEndpoint endpoint = new HttpEndpoint(endpointUriString, this, clientParams, clientConnectionManager, configurer);
        if (urlRewrite != null) {
            // let CamelContext deal with the lifecycle of the url rewrite
            // this ensures its being shutdown when Camel shutdown etc.
            getCamelContext().addService(urlRewrite);
            endpoint.setUrlRewrite(urlRewrite);
        }
        // configure the endpoint
        setProperties(endpoint, parameters);

        // determine the portnumber (special case: default portnumber)
        int port = getPort(uriHttpUriAddress);

        // we can not change the port of an URI, we must create a new one with an explicit port value
        URI httpUri = URISupport.createRemainingURI(
                new URI(uriHttpUriAddress.getScheme(),
                        uriHttpUriAddress.getUserInfo(),
                        uriHttpUriAddress.getHost(),
                        port,
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
        // register port on schema registry
        registerPort(secure, x509HostnameVerifier, port, sslContextParameters);

        return endpoint;
    }
   
    private static int getPort(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            if ("http4".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                port = 80;
            } else if ("https4".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                port = 443;
            } else {
                throw new IllegalArgumentException("Unknown scheme, cannot determine port number for uri: " + uri);
            }
        }
        return port;
    }
    
    @SuppressWarnings("deprecation")
    protected void registerPort(boolean secure, X509HostnameVerifier x509HostnameVerifier, int port, SSLContextParameters sslContextParams) throws Exception {
        if (usedSslContextParams == null) {
            usedSslContextParams = sslContextParams;
        }

        // we must use same SSLContextParameters for this component.
        if (usedSslContextParams != sslContextParams) {
            // use identity hashcode in exception message
            Object previous = ObjectHelper.getIdentityHashCode(usedSslContextParams);
            Object next = ObjectHelper.getIdentityHashCode(sslContextParams);
            throw new IllegalArgumentException("Only same instance of SSLContextParameters is supported. Cannot use a different instance."
                    + " Previous instance hashcode: " + previous + ", New instance hashcode: " + next);
        }

        SchemeRegistry registry = clientConnectionManager.getSchemeRegistry();
        if (secure) {
            SSLSocketFactory socketFactory;
            if (sslContextParams == null) {
                socketFactory = SSLSocketFactory.getSocketFactory();
            } else {
                socketFactory = new SSLSocketFactory(sslContextParams.createSSLContext());
            }

            socketFactory.setHostnameVerifier(x509HostnameVerifier);
            // must register both https and https4
            registry.register(new Scheme("https", port, socketFactory));
            LOG.info("Registering SSL scheme https on port " + port);
            
            registry.register(new Scheme("https4", port, socketFactory));
            LOG.info("Registering SSL scheme https4 on port " + port);
        } else {
            // must register both http and http4
            registry.register(new Scheme("http", port, new PlainSocketFactory()));
            LOG.info("Registering PLAIN scheme http on port " + port);
            registry.register(new Scheme("http4", port, new PlainSocketFactory()));
            LOG.info("Registering PLAIN scheme http4 on port " + port);
        }
    }

    protected ClientConnectionManager createConnectionManager() {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        // setup the connection live time
        PoolingClientConnectionManager answer = new PoolingClientConnectionManager(schemeRegistry, getConnectionTimeToLive(), TimeUnit.MILLISECONDS);
        if (getMaxTotalConnections() > 0) {
            answer.setMaxTotal(getMaxTotalConnections());
        }
        if (getConnectionsPerRoute() > 0) {
            answer.setDefaultMaxPerRoute(getConnectionsPerRoute());
        }
        
        LOG.info("Created ClientConnectionManager " + answer);

        return answer;
    }

    protected HttpParams configureHttpParams(Map<String, Object> parameters) throws Exception {
        HttpParams clientParams = new BasicHttpParams();

        AuthParamBean authParamBean = new AuthParamBean(clientParams);
        IntrospectionSupport.setProperties(authParamBean, parameters, "httpClient.");

        ClientParamBean clientParamBean = new ClientParamBean(clientParams);
        IntrospectionSupport.setProperties(clientParamBean, parameters, "httpClient.");
        
        ConnRouteParamBean connRouteParamBean = new ConnRouteParamBean(clientParams);
        IntrospectionSupport.setProperties(connRouteParamBean, parameters, "httpClient.");

        CookieSpecParamBean cookieSpecParamBean = new CookieSpecParamBean(clientParams);
        IntrospectionSupport.setProperties(cookieSpecParamBean, parameters, "httpClient.");

        HttpConnectionParamBean httpConnectionParamBean = new HttpConnectionParamBean(clientParams);
        IntrospectionSupport.setProperties(httpConnectionParamBean, parameters, "httpClient.");

        HttpProtocolParamBean httpProtocolParamBean = new HttpProtocolParamBean(clientParams);
        IntrospectionSupport.setProperties(httpProtocolParamBean, parameters, "httpClient.");

        return clientParams;
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        return false;
    }

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
    }

    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
    }

    public ClientConnectionManager getClientConnectionManager() {
        return clientConnectionManager;
    }

    public void setClientConnectionManager(ClientConnectionManager clientConnectionManager) {
        this.clientConnectionManager = clientConnectionManager;
    }

    public HttpBinding getHttpBinding() {
        return httpBinding;
    }

    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public X509HostnameVerifier getX509HostnameVerifier() {
        return x509HostnameVerifier;
    }

    public void setX509HostnameVerifier(X509HostnameVerifier x509HostnameVerifier) {
        this.x509HostnameVerifier = x509HostnameVerifier;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public int getConnectionsPerRoute() {
        return connectionsPerRoute;
    }

    public void setConnectionsPerRoute(int connectionsPerRoute) {
        this.connectionsPerRoute = connectionsPerRoute;
    }
    
    public long getConnectionTimeToLive() {
        return connectionTimeToLive;
    }
    
    public void setConnectionTimeToLive(long connectionTimeToLive) {
        this.connectionTimeToLive = connectionTimeToLive;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        if (clientConnectionManager == null) {
            clientConnectionManager = createConnectionManager();
        }
    }

    @Override
    public void doStop() throws Exception {
        // shutdown connection manager
        if (clientConnectionManager != null) {
            LOG.info("Shutting down ClientConnectionManager: " + clientConnectionManager);
            clientConnectionManager.shutdown();
            clientConnectionManager = null;
        }
        usedSslContextParams = null;
        super.doStop();
    }
}
