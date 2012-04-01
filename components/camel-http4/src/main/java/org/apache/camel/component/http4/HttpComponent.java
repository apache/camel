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

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.http4.helper.HttpHelper;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.auth.params.AuthParamBean;
import org.apache.http.client.params.ClientParamBean;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnConnectionParamBean;
import org.apache.http.conn.params.ConnRouteParamBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.cookie.params.CookieSpecParamBean;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParamBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/http4.html">HTTP4
 * Component</a>
 *
 * @version 
 */
public class HttpComponent extends HeaderFilterStrategyComponent {
    private static final transient Logger LOG = LoggerFactory.getLogger(HttpComponent.class);

    protected HttpClientConfigurer httpClientConfigurer;
    protected ClientConnectionManager clientConnectionManager;
    protected HttpBinding httpBinding;
    protected SSLContextParameters sslContextParameters;
    protected X509HostnameVerifier x509HostnameVerifier = new BrowserCompatHostnameVerifier();

    // options to the default created http connection manager
    protected int maxTotalConnections = 200;
    protected int connectionsPerRoute = 20;

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
            // fallback and use either http4 or https4 depending on secure
            proxyAuthScheme = secure ? "https4" : "http4";
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
        String addressUri = uri;
        if (!uri.startsWith("http4:") && !uri.startsWith("https4:")) {
            addressUri = remaining;
        }
        Map<String, Object> httpClientParameters = new HashMap<String, Object>(parameters);
        // http client can be configured from URI options
        HttpParams clientParams = configureHttpParams(parameters);
        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, parameters, "httpClient.");
        
        HttpBinding httpBinding = resolveAndRemoveReferenceParameter(parameters, "httpBindingRef", HttpBinding.class);
        if (httpBinding == null) {
            httpBinding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        }
        
        HttpClientConfigurer httpClientConfigurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurerRef", HttpClientConfigurer.class);
        if (httpClientConfigurer == null) {
            httpClientConfigurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurer", HttpClientConfigurer.class);
        }
        
        X509HostnameVerifier x509HostnameVerifier = resolveAndRemoveReferenceParameter(parameters, "x509HostnameVerifier", X509HostnameVerifier.class);
        if (x509HostnameVerifier == null) {
            x509HostnameVerifier = this.x509HostnameVerifier;
        }
        
        SSLContextParameters sslContextParameters = resolveAndRemoveReferenceParameter(parameters, "sslContextParametersRef", SSLContextParameters.class);
        if (sslContextParameters == null) {
            sslContextParameters = this.sslContextParameters;
        }
        
        boolean secure = HttpHelper.isSecureConnection(uri);

        // create the configurer to use for this endpoint
        HttpClientConfigurer configurer = createHttpClientConfigurer(parameters, secure);
        URI endpointUri = URISupport.createRemainingURI(new URI(addressUri), httpClientParameters);
        // create the endpoint and set the http uri to be null
        HttpEndpoint endpoint = new HttpEndpoint(endpointUri.toString(), this, clientParams, clientConnectionManager, configurer);
        // configure the endpoint
        setProperties(endpoint, parameters);
        // The httpUri should be start with http or https
        String httpUriAddress = addressUri;
        if (addressUri.startsWith("http4")) {
            httpUriAddress = "http" + addressUri.substring(5);
        }
        if (addressUri.startsWith("https4")) {
            httpUriAddress = "https" + addressUri.substring(6);
        }
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        // build up the http uri
        URI httpUri = URISupport.createRemainingURI(new URI(httpUriAddress), parameters);

        // validate http uri that end-user did not duplicate the http part that can be a common error
        String part = httpUri.getSchemeSpecificPart();
        if (part != null) {
            part = part.toLowerCase();
            if (part.startsWith("//http//") || part.startsWith("//https//") || part.startsWith("//http://") || part.startsWith("//https://")) {
                throw new ResolveEndpointFailedException(uri,
                        "The uri part is not configured correctly. You have duplicated the http(s) protocol.");
            }
        }
        endpoint.setHttpUri(httpUri);
        setEndpointHeaderFilterStrategy(endpoint);
        endpoint.setBinding(getHttpBinding());
        if (httpBinding != null) {
            endpoint.setHttpBinding(httpBinding);
        }
        if (httpClientConfigurer != null) {
            endpoint.setHttpClientConfigurer(httpClientConfigurer);
        }
        // register port on schema registry
        int port = getPort(httpUri);
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

        ThreadSafeClientConnManager answer = new ThreadSafeClientConnManager(schemeRegistry);
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

        ConnConnectionParamBean connConnectionParamBean = new ConnConnectionParamBean(clientParams);
        IntrospectionSupport.setProperties(connConnectionParamBean, parameters, "httpClient.");

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
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
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
        super.doStop();
    }
}
