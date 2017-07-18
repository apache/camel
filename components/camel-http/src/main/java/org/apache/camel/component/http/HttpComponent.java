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
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonComponent;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.http.common.HttpRestHeaderFilterStrategy;
import org.apache.camel.http.common.UrlRewrite;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

/**
 * The <a href="http://camel.apache.org/http.html">HTTP Component</a>
 *
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class HttpComponent extends HttpCommonComponent implements RestProducerFactory, VerifiableComponent, SSLContextParametersAware {

    @Metadata(label = "advanced")
    protected HttpClientConfigurer httpClientConfigurer;
    @Metadata(label = "advanced")
    protected HttpConnectionManager httpConnectionManager;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public HttpComponent() {
        this(HttpEndpoint.class);
    }

    public HttpComponent(Class<? extends HttpEndpoint> endpointClass) {
        super(endpointClass);

        registerExtension(HttpComponentVerifierExtension::new);
    }

    /**
     * Creates the HttpClientConfigurer based on the given parameters
     * 
     * @param parameters the map of parameters 
     * @return the configurer
     */
    protected HttpClientConfigurer createHttpClientConfigurer(Map<String, Object> parameters, Set<AuthMethod> authMethods) {
        // prefer to use endpoint configured over component configured
        HttpClientConfigurer configurer = resolveAndRemoveReferenceParameter(parameters, "httpClientConfigurer", HttpClientConfigurer.class);
        if (configurer == null) {
            // fallback to component configured
            configurer = getHttpClientConfigurer();
        }

        // authentication can be endpoint configured
        String authUsername = getParameter(parameters, "authUsername", String.class);
        String authMethod = getParameter(parameters, "authMethod", String.class);
        // validate that if auth username is given then the auth method is also provided
        if (authUsername != null && authMethod == null) {
            throw new IllegalArgumentException("Option authMethod must be provided to use authentication");
        }
        if (authMethod != null) {
            String authPassword = getParameter(parameters, "authPassword", String.class);
            String authDomain = getParameter(parameters, "authDomain", String.class);
            String authHost = getParameter(parameters, "authHost", String.class);
            configurer = configureAuth(configurer, authMethod, authUsername, authPassword, authDomain, authHost, authMethods);
        } else if (httpConfiguration != null) {
            // or fallback to use component configuration
            configurer = configureAuth(configurer, httpConfiguration.getAuthMethod(), httpConfiguration.getAuthUsername(),
                    httpConfiguration.getAuthPassword(), httpConfiguration.getAuthDomain(), httpConfiguration.getAuthHost(), authMethods);
        }

        // proxy authentication can be endpoint configured
        String proxyAuthUsername = getParameter(parameters, "proxyAuthUsername", String.class);
        String proxyAuthMethod = getParameter(parameters, "proxyAuthMethod", String.class);
        // validate that if proxy auth username is given then the proxy auth method is also provided
        if (proxyAuthUsername != null && proxyAuthMethod == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }
        if (proxyAuthMethod != null) {
            String proxyAuthPassword = getParameter(parameters, "proxyAuthPassword", String.class);
            String proxyAuthDomain = getParameter(parameters, "proxyAuthDomain", String.class);
            String proxyAuthHost = getParameter(parameters, "proxyAuthHost", String.class);
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
    protected HttpClientConfigurer configureAuth(HttpClientConfigurer configurer, String authMethod, String username,
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

        AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, authMethod);

        // add it as a auth method used
        authMethods.add(auth);

        if (auth == AuthMethod.Basic || auth == AuthMethod.Digest) {
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new BasicAuthenticationHttpClientConfigurer(false, username, password));
        } else if (auth == AuthMethod.NTLM) {
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
    protected HttpClientConfigurer configureProxyAuth(HttpClientConfigurer configurer, String authMethod, String username,
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

        AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, authMethod);

        // add it as a auth method used
        authMethods.add(auth);

        if (auth == AuthMethod.Basic || auth == AuthMethod.Digest) {
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new BasicAuthenticationHttpClientConfigurer(true, username, password));
        } else if (auth == AuthMethod.NTLM) {
            // domain is mandatory for NTML
            ObjectHelper.notNull(domain, "proxyAuthDomain");
            return CompositeHttpConfigurer.combineConfigurers(configurer,
                    new NTLMAuthenticationHttpClientConfigurer(true, username, password, domain, host));
        }

        throw new IllegalArgumentException("Unknown proxyAuthMethod " + authMethod);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String addressUri = "http://" + remaining;
        if (uri.startsWith("https:")) {
            addressUri = "https://" + remaining;
        }
        Map<String, Object> httpClientParameters = new HashMap<String, Object>(parameters);
        // must extract well known parameters before we create the endpoint
        HttpBinding binding = resolveAndRemoveReferenceParameter(parameters, "httpBinding", HttpBinding.class);
        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);
        UrlRewrite urlRewrite = resolveAndRemoveReferenceParameter(parameters, "urlRewrite", UrlRewrite.class);
        // http client can be configured from URI options
        HttpClientParams clientParams = new HttpClientParams();
        Map<String, Object> httpClientOptions = IntrospectionSupport.extractProperties(parameters, "httpClient.");
        IntrospectionSupport.setProperties(clientParams, httpClientOptions);
        // validate that we could resolve all httpClient. parameters as this component is lenient
        validateParameters(uri, httpClientOptions, null);
        // http client can be configured from URI options
        HttpConnectionManagerParams connectionManagerParams = new HttpConnectionManagerParams();
        // setup the httpConnectionManagerParams
        Map<String, Object> httpConnectionManagerOptions = IntrospectionSupport.extractProperties(parameters, "httpConnectionManager.");
        IntrospectionSupport.setProperties(connectionManagerParams, httpConnectionManagerOptions);
        // validate that we could resolve all httpConnectionManager. parameters as this component is lenient
        validateParameters(uri, httpConnectionManagerOptions, null);
        // make sure the component httpConnectionManager is take effect
        HttpConnectionManager thisHttpConnectionManager = httpConnectionManager;
        if (thisHttpConnectionManager == null) {
            // only set the params on the new created http connection manager
            thisHttpConnectionManager = new MultiThreadedHttpConnectionManager();
            thisHttpConnectionManager.setParams(connectionManagerParams);
        }
        // create the configurer to use for this endpoint (authMethods contains the used methods created by the configurer)
        final Set<AuthMethod> authMethods = new LinkedHashSet<AuthMethod>();
        HttpClientConfigurer configurer = createHttpClientConfigurer(parameters, authMethods);
        addressUri = UnsafeUriCharactersEncoder.encodeHttpURI(addressUri);
        URI endpointUri = URISupport.createRemainingURI(new URI(addressUri), httpClientParameters);
       
        // create the endpoint and connectionManagerParams already be set
        HttpEndpoint endpoint = createHttpEndpoint(endpointUri.toString(), this, clientParams, thisHttpConnectionManager, configurer);

        // configure the endpoint with the common configuration from the component
        if (getHttpConfiguration() != null) {
            Map<String, Object> properties = new HashMap<>();
            IntrospectionSupport.getProperties(getHttpConfiguration(), properties, null);
            setProperties(endpoint, properties);
        }

        if (headerFilterStrategy != null) {
            endpoint.setHeaderFilterStrategy(headerFilterStrategy);
        } else {
            setEndpointHeaderFilterStrategy(endpoint);
        }
        if (urlRewrite != null) {
            // let CamelContext deal with the lifecycle of the url rewrite
            // this ensures its being shutdown when Camel shutdown etc.
            getCamelContext().addService(urlRewrite);
            endpoint.setUrlRewrite(urlRewrite);
        }

        // prefer to use endpoint configured over component configured
        if (binding == null) {
            // fallback to component configured
            binding = getHttpBinding();
        }
        if (binding != null) {
            endpoint.setBinding(binding);
        }
        setProperties(endpoint, parameters);
        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), parameters);
        
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
        endpoint.setHttpClientOptions(httpClientOptions);
        return endpoint;
    }

    protected HttpEndpoint createHttpEndpoint(String uri, HttpComponent component, HttpClientParams clientParams,
                                              HttpConnectionManager connectionManager, HttpClientConfigurer configurer) throws URISyntaxException {
        return new HttpEndpoint(uri, component, clientParams, connectionManager, configurer);
    }

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {

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

        HttpEndpoint endpoint = camelContext.getEndpoint(url, HttpEndpoint.class);
        if (parameters != null && !parameters.isEmpty()) {
            setProperties(camelContext, endpoint, parameters);
        }
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

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    /**
     * To use a custom HttpConnectionManager to manage connections
     */
    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    @Override
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

    @Override
    public ComponentVerifier getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
