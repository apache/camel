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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

/**
 * Defines the <a href="http://camel.apache.org/http.html">HTTP
 * Component</a>
 *
 * @version $Revision$
 */
public class HttpComponent extends HeaderFilterStrategyComponent {
    protected HttpClientConfigurer httpClientConfigurer;
    protected HttpConnectionManager httpConnectionManager = new MultiThreadedHttpConnectionManager();
    protected HttpBinding httpBinding;
    protected HttpConfiguration httpConfiguration;

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
     * @return the configurer
     */
    protected HttpClientConfigurer createHttpClientConfigurer(Map<String, Object> parameters, Set<AuthMethod> authMethods) {
        // prefer to use endpoint configured over component configured
        HttpClientConfigurer configurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurerRef", HttpClientConfigurer.class);
        if (configurer == null) {
            // try without ref
            configurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurer", HttpClientConfigurer.class);
        }
        if (configurer == null) {
            // fallback to component configured
            configurer = getHttpClientConfigurer();
        }

        // authentication can be endpoint configured
        String authUsername = getAndRemoveParameter(parameters, "authUsername", String.class);
        AuthMethod authMethod = getAndRemoveParameter(parameters, "authMethod", AuthMethod.class);
        // validate that if auth username is given then the auth method is also provided
        if (authUsername != null && authMethod == null) {
            throw new IllegalArgumentException("Option authMethod must be provided to use authentication");
        }
        if (authMethod != null) {
            String authPassword = getAndRemoveParameter(parameters, "authPassword", String.class);
            String authDomain = getAndRemoveParameter(parameters, "authDomain", String.class);
            String authHost = getAndRemoveParameter(parameters, "authHost", String.class);
            configurer = configureAuth(configurer, authMethod, authUsername, authPassword, authDomain, authHost, authMethods);
        } else if (httpConfiguration != null) {
            // or fallback to use component configuration
            configurer = configureAuth(configurer, httpConfiguration.getAuthMethod(), httpConfiguration.getAuthUsername(),
                    httpConfiguration.getAuthPassword(), httpConfiguration.getAuthDomain(), httpConfiguration.getAuthHost(), authMethods);
        }

        // proxy authentication can be endpoint configured
        String proxyAuthUsername = getAndRemoveParameter(parameters, "proxyAuthUsername", String.class);
        AuthMethod proxyAuthMethod = getAndRemoveParameter(parameters, "proxyAuthMethod", AuthMethod.class);
        // validate that if proxy auth username is given then the proxy auth method is also provided
        if (proxyAuthUsername != null && proxyAuthMethod == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }
        if (proxyAuthMethod != null) {
            String proxyAuthPassword = getAndRemoveParameter(parameters, "proxyAuthPassword", String.class);
            String proxyAuthDomain = getAndRemoveParameter(parameters, "proxyAuthDomain", String.class);
            String proxyAuthHost = getAndRemoveParameter(parameters, "proxyAuthHost", String.class);
            configurer = configureProxyAuth(configurer, proxyAuthMethod, proxyAuthUsername, proxyAuthPassword, proxyAuthDomain, proxyAuthHost, authMethods);
        } else if (httpConfiguration != null) {
            // or fallback to use component configuration
            configurer = configureProxyAuth(configurer, httpConfiguration.getProxyAuthMethod(), httpConfiguration.getProxyAuthUsername(),
                    httpConfiguration.getProxyAuthPassword(), httpConfiguration.getProxyAuthDomain(), httpConfiguration.getProxyAuthHost(), authMethods);
        }

        return configurer;
    }

    /**
     * Configures the authentication method to be used
     *
     * @return configurer to used
     */
    protected HttpClientConfigurer configureAuth(HttpClientConfigurer configurer, AuthMethod authMethod, String username,
                                                 String password, String domain, String host, Set<AuthMethod> authMethods) {

        // no auth is in use
        if (username == null && authMethod == null) {
            return configurer;
        }

        // validate mandatory options given
        if (username != null && authMethod == null) {
            throw new IllegalArgumentException("Option authMethod must be provided to use authentication");
        }
        ObjectHelper.notNull(authMethod, "authMethod");
        ObjectHelper.notNull(username, "authUsername");
        ObjectHelper.notNull(password, "authPassword");

        // add it as a auth method used
        authMethods.add(authMethod);

        if (authMethod == AuthMethod.Basic || authMethod == AuthMethod.Digest) {
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new BasicAuthenticationHttpClientConfigurer(false, username, password));
        } else if (authMethod == AuthMethod.NTLM) {
            // domain is mandatory for NTLM
            ObjectHelper.notNull(domain, "authDomain");
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new NTLMAuthenticationHttpClientConfigurer(false, username, password, domain, host));
        }

        throw new IllegalArgumentException("Unknown authMethod " + authMethod);
    }
    
    /**
     * Configures the proxy authentication method to be used
     *
     * @return configurer to used
     */
    protected HttpClientConfigurer configureProxyAuth(HttpClientConfigurer configurer, AuthMethod authMethod, String username,
                                                      String password, String domain, String host, Set<AuthMethod> authMethods) {
        // no proxy auth is in use
        if (username == null && authMethod == null) {
            return configurer;
        }

        // validate mandatory options given
        if (username != null && authMethod == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }
        ObjectHelper.notNull(authMethod, "proxyAuthMethod");
        ObjectHelper.notNull(username, "proxyAuthUsername");
        ObjectHelper.notNull(password, "proxyAuthPassword");

        // add it as a auth method used
        authMethods.add(authMethod);

        if (authMethod == AuthMethod.Basic || authMethod == AuthMethod.Digest) {
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new BasicAuthenticationHttpClientConfigurer(true, username, password));
        } else if (authMethod == AuthMethod.NTLM) {
            // domain is mandatory for NTML
            ObjectHelper.notNull(domain, "proxyAuthDomain");
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new NTLMAuthenticationHttpClientConfigurer(true, username, password, domain, host));
        }

        throw new IllegalArgumentException("Unknown proxyAuthMethod " + authMethod);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String addressUri = uri;
        if (!uri.startsWith("http:") && !uri.startsWith("https:")) {
            addressUri = remaining;
        }
        Map<String, Object> httpClientParameters = new HashMap<String, Object>(parameters);
        // must extract well known parameters before we create the endpoint
        HttpBinding binding = resolveAndRemoveReferenceParameter(parameters, "httpBindingRef", HttpBinding.class);
        if (binding == null) {
            // try without ref
            binding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        }
        Boolean throwExceptionOnFailure = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class);
        Boolean bridgeEndpoint = getAndRemoveParameter(parameters, "bridgeEndpoint", Boolean.class);
        Boolean matchOnUriPrefix = getAndRemoveParameter(parameters, "matchOnUriPrefix", Boolean.class);
        Boolean disableStreamCache = getAndRemoveParameter(parameters, "disableStreamCache", Boolean.class);
        String proxyHost = getAndRemoveParameter(parameters, "proxyHost", String.class);
        Integer proxyPort = getAndRemoveParameter(parameters, "proxyPort", Integer.class);
        String authMethodPriority = getAndRemoveParameter(parameters, "authMethodPriority", String.class);
        // http client can be configured from URI options
        HttpClientParams clientParams = new HttpClientParams();
        IntrospectionSupport.setProperties(clientParams, parameters, "httpClient.");
        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, parameters, "httpClient.");       
        
        // create the configurer to use for this endpoint (authMethods contains the used methods created by the configurer)
        final Set<AuthMethod> authMethods = new LinkedHashSet<AuthMethod>();
        HttpClientConfigurer configurer = createHttpClientConfigurer(parameters, authMethods);
        URI endpointUri = URISupport.createRemainingURI(new URI(addressUri), CastUtils.cast(httpClientParameters));
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), CastUtils.cast(parameters));
        
        // validate http uri that end-user did not duplicate the http part that can be a common error
        String part = httpUri.getSchemeSpecificPart();
        if (part != null) {
            part = part.toLowerCase();
            if (part.startsWith("//http//") || part.startsWith("//https//")) {
                throw new ResolveEndpointFailedException(uri,
                        "The uri part is not configured correctly. You have duplicated the http(s) protocol.");
            }
        }
        // need to keep the parameters of http client configure to avoid unwiser endpoint caching

        // create the endpoint
        HttpEndpoint endpoint = new HttpEndpoint(endpointUri.toString(), this, httpUri, clientParams, httpConnectionManager, configurer);
        setEndpointHeaderFilterStrategy(endpoint);

        // prefer to use endpoint configured over component configured
        if (binding == null) {
            // fallback to component configured
            binding = getHttpBinding();
        }
        if (binding != null) {
            endpoint.setBinding(binding);
        }
        // should we use an exception for failed error codes?
        if (throwExceptionOnFailure != null) {
            endpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        if (bridgeEndpoint != null) {
            endpoint.setBridgeEndpoint(bridgeEndpoint);
        }
        if (matchOnUriPrefix != null) {
            endpoint.setMatchOnUriPrefix(matchOnUriPrefix);
        }
        if (disableStreamCache != null) {
            endpoint.setDisableStreamCache(disableStreamCache);
        }
        if (proxyHost != null) {
            endpoint.setProxyHost(proxyHost);
            endpoint.setProxyPort(proxyPort);
        } else if (httpConfiguration != null) {
            endpoint.setProxyHost(httpConfiguration.getProxyHost());
            endpoint.setProxyPort(httpConfiguration.getProxyPort());
        }
        if (authMethodPriority != null) {
            endpoint.setAuthMethodPriority(authMethodPriority);
        } else if (httpConfiguration != null && httpConfiguration.getAuthMethodPriority() != null) {
            endpoint.setAuthMethodPriority(httpConfiguration.getAuthMethodPriority());
        } else {
            // no explicit auth method priority configured, so use convention over configuration
            // and set priority based on auth method
            if (!authMethods.isEmpty()) {
                authMethodPriority = CollectionHelper.collectionAsCommaDelimitedString(authMethods);
                endpoint.setAuthMethodPriority(authMethodPriority);
            }
        }

        setProperties(endpoint, parameters);
        return endpoint;
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

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
    }

    public HttpBinding getHttpBinding() {
        return httpBinding;
    }

    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    public HttpConfiguration getHttpConfiguration() {
        return httpConfiguration;
    }

    public void setHttpConfiguration(HttpConfiguration httpConfiguration) {
        this.httpConfiguration = httpConfiguration;
    }

}
