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
package org.apache.camel.component.http;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.http.auth.params.AuthParamBean;
import org.apache.http.client.params.ClientParamBean;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnConnectionParamBean;
import org.apache.http.conn.params.ConnManagerParamBean;
import org.apache.http.conn.params.ConnRouteParamBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.params.CookieSpecParamBean;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParamBean;

/**
 * Defines the <a href="http://camel.apache.org/http.html">HTTP
 * Component</a>
 *
 * @version $Revision$
 */
public class HttpComponent extends HeaderFilterStrategyComponent {
    protected HttpClientConfigurer httpClientConfigurer;
    protected ClientConnectionManager httpConnectionManager;
    protected HttpBinding httpBinding;


    /**
     * Connects the URL specified on the endpoint to the specified processor.
     *
     * @param  consumer the consumer
     * @throws Exception can be thrown
     */
    public void connect(HttpConsumer consumer) throws Exception {
    }

    /**
     * Disconnects the URL specified on the endpoint from the specified processor.
     *
     * @param  consumer the consumer
     * @throws Exception can be thrown
     */
    public void disconnect(HttpConsumer consumer) throws Exception {
    }

    /**
     * Setting http binding and http client configurer according to the parameters
     * Also setting the BasicAuthenticationHttpClientConfigurer if the username
     * and password option are not null.
     *
     * @param parameters the map of parameters
     */
    protected void configureParameters(Map<String, Object> parameters) {
        // lookup http binding in registry if provided
        httpBinding = resolveAndRemoveReferenceParameter(
                parameters, "httpBindingRef", HttpBinding.class);

        // lookup http client front configurer in the registry if provided
        httpClientConfigurer = resolveAndRemoveReferenceParameter(
                parameters, "httpClientConfigurerRef", HttpClientConfigurer.class);

        // check the user name and password for basic authentication
        String username = getAndRemoveParameter(parameters, "username", String.class);
        String password = getAndRemoveParameter(parameters, "password", String.class);
        String domain = getAndRemoveParameter(parameters, "domain", String.class);
        String host = getAndRemoveParameter(parameters, "host", String.class);
        if (username != null && password != null) {
            httpClientConfigurer = CompositeHttpConfigurer.combineConfigurers(
                    httpClientConfigurer,
                    new BasicAuthenticationHttpClientConfigurer(username, password, domain, host));
        }

        // check the proxy details for proxy configuration
        String proxyHost = getAndRemoveParameter(parameters, "proxyHost", String.class);
        Integer proxyPort = getAndRemoveParameter(parameters, "proxyPort", Integer.class);
        if (proxyHost != null && proxyPort != null) {
            String proxyUsername = getAndRemoveParameter(parameters, "proxyUsername", String.class);
            String proxyPassword = getAndRemoveParameter(parameters, "proxyPassword", String.class);
            String proxyDomain = getAndRemoveParameter(parameters, "proxyDomain", String.class);
            String proxyNtHost = getAndRemoveParameter(parameters, "proxyNtHost", String.class);
            if (proxyUsername != null && proxyPassword != null) {
                httpClientConfigurer = CompositeHttpConfigurer.combineConfigurers(
                        httpClientConfigurer, new ProxyHttpClientConfigurer(proxyHost, proxyPort, proxyUsername, proxyPassword, proxyDomain, proxyNtHost));
            } else {
                httpClientConfigurer = CompositeHttpConfigurer.combineConfigurers(
                        httpClientConfigurer, new ProxyHttpClientConfigurer(proxyHost, proxyPort));
            }
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // http client can be configured from URI options
        HttpParams clientParams = configureHttpParams(parameters);

        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, parameters, "httpClient.");

        configureParameters(parameters);

        // should we use an exception for failed error codes?
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);

        Boolean bridgeEndpoint = getAndRemoveParameter(parameters, "bridgeEndpoint", Boolean.class);

        Boolean matchOnUriPrefix = Boolean.parseBoolean(getAndRemoveParameter(parameters, "matchOnUriPrefix", String.class));
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(uri), CastUtils.cast(parameters));
        uri = httpUri.toString();

        // validate http uri that end-user did not duplicate the http part that can be a common error
        String part = httpUri.getSchemeSpecificPart();
        if (part != null) {
            part = part.toLowerCase();
            if (part.startsWith("//http//") || part.startsWith("//https//")) {
                throw new ResolveEndpointFailedException(uri,
                        "The uri part is not configured correctly. You have duplicated the http(s) protocol.");
            }
        }

        ClientConnectionManager connMgr = httpConnectionManager;
        if (connMgr == null) {
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            if (isSecureConnection(uri)) {
                schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
            } else {
                schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            }

            connMgr = new ThreadSafeClientConnManager(clientParams, schemeRegistry);
        }

        HttpEndpoint endpoint = new HttpEndpoint(uri, this, httpUri, clientParams, connMgr, httpClientConfigurer);
        if (httpBinding != null) {
            endpoint.setBinding(httpBinding);
        }
        setEndpointHeaderFilterStrategy(endpoint);
        if (throwExceptionOnFailure != null) {
            endpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        if (bridgeEndpoint != null) {
            endpoint.setBridgeEndpoint(bridgeEndpoint);
        }
        if (matchOnUriPrefix != null) {
            endpoint.setMatchOnUriPrefix(matchOnUriPrefix);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private boolean isSecureConnection(String uri) {
        return uri.startsWith("https");
    }

    protected HttpParams configureHttpParams(Map<String, Object> parameters) throws Exception {
        HttpParams clientParams = new BasicHttpParams();

        AuthParamBean authParamBean = new AuthParamBean(clientParams);
        IntrospectionSupport.setProperties(authParamBean, parameters, "httpClient.");

        ClientParamBean clientParamBean = new ClientParamBean(clientParams);
        IntrospectionSupport.setProperties(clientParamBean, parameters, "httpClient.");

        ConnConnectionParamBean connConnectionParamBean = new ConnConnectionParamBean(clientParams);
        IntrospectionSupport.setProperties(connConnectionParamBean, parameters, "httpClient.");

        ConnManagerParamBean connManagerParamBean = new ConnManagerParamBean(clientParams);
        IntrospectionSupport.setProperties(connManagerParamBean, parameters, "httpClient.");

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

    public ClientConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    public void setHttpConnectionManager(ClientConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
    }

    public HttpBinding getHttpBinding() {
        return httpBinding;
    }

    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

}