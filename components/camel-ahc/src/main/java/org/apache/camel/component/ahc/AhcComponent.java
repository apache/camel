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
package org.apache.camel.component.ahc;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Realm;
import org.asynchttpclient.Realm.Builder;
import org.asynchttpclient.cookie.CookieStore;
import org.asynchttpclient.cookie.ThreadSafeCookieStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  To call external HTTP services using <a href="http://github.com/sonatype/async-http-client">Async Http Client</a>
 */
@Component("ahc")
public class AhcComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(AhcComponent.class);

    private static final String CLIENT_CONFIG_PREFIX = "clientConfig.";
    private static final String CLIENT_REALM_CONFIG_PREFIX = "clientConfig.realm.";

    @Metadata(label = "advanced")
    private AsyncHttpClient client;
    @Metadata(label = "advanced")
    private AsyncHttpClientConfig clientConfig;
    @Metadata(label = "advanced")
    private AhcBinding binding;
    @Metadata(label = "security")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced")
    private boolean allowJavaSerializedObject;

    public AhcComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String addressUri = createAddressUri(uri, remaining);

        SSLContextParameters ssl = getSslContextParameters();
        if (ssl == null) {
            ssl = retrieveGlobalSslContextParameters();
        }

        // Do not set the HTTP URI because we still have all of the Camel internal
        // parameters in the URI at this point.
        AhcEndpoint endpoint = createAhcEndpoint(uri, this, null);
        setEndpointHeaderFilterStrategy(endpoint);
        endpoint.setClient(getClient());
        endpoint.setClientConfig(getClientConfig());
        endpoint.setBinding(getBinding());
        endpoint.setSslContextParameters(ssl);
        
        setProperties(endpoint, parameters);

        if (PropertiesHelper.hasProperties(parameters, CLIENT_CONFIG_PREFIX)) {
            DefaultAsyncHttpClientConfig.Builder builder = endpoint.getClientConfig() == null
                    ? new DefaultAsyncHttpClientConfig.Builder() : AhcComponent.cloneConfig(endpoint.getClientConfig());
            
            if (endpoint.getClient() != null) {
                LOG.warn("The user explicitly set an AsyncHttpClient instance on the component or "
                         + "endpoint, but this endpoint URI contains client configuration parameters.  "
                         + "Are you sure that this is what was intended?  The AsyncHttpClient will be used"
                         + " and the URI parameters will be ignored.");
            } else if (endpoint.getClientConfig() != null) {
                LOG.warn("The user explicitly set an AsyncHttpClientConfig instance on the component or "
                         + "endpoint, but this endpoint URI contains client configuration parameters.  "
                         + "Are you sure that this is what was intended?  The URI parameters will be applied"
                         + " to a clone of the supplied AsyncHttpClientConfig in order to prevent unintended modification"
                         + " of the explicitly configured AsyncHttpClientConfig.  That is, the URI parameters override the"
                         + " settings on the explicitly configured AsyncHttpClientConfig for this endpoint.");
            }

            // special for realm builder
            Builder realmBuilder = null;
            if (PropertiesHelper.hasProperties(parameters, CLIENT_REALM_CONFIG_PREFIX)) {

                // set and validate additional parameters on client config
                Map<String, Object> realmParams = PropertiesHelper.extractProperties(parameters, CLIENT_REALM_CONFIG_PREFIX);

                // copy the parameters for the endpoint to have
                endpoint.setClientConfigRealmOptions(new LinkedHashMap<>(realmParams));

                Object principal = realmParams.remove("principal");
                Object password = realmParams.remove("password");

                if (ObjectHelper.isEmpty(principal)) {
                    throw new IllegalArgumentException(CLIENT_REALM_CONFIG_PREFIX + ".principal must be configured");
                }
                if (password == null) {
                    password = "";
                }

                realmBuilder = new Realm.Builder(principal.toString(), password.toString());
                setProperties(realmBuilder, realmParams);
                validateParameters(uri, realmParams, null);
            }
            
            // set and validate additional parameters on client config
            Map<String, Object> clientParams = PropertiesHelper.extractProperties(parameters, CLIENT_CONFIG_PREFIX);

            // copy the parameters for the endpoint to have
            endpoint.setClientConfigOptions(new LinkedHashMap<>(clientParams));

            setProperties(builder, clientParams);
            validateParameters(uri, clientParams, null);

            if (realmBuilder != null) {
                builder.setRealm(realmBuilder.build());
            }
            endpoint.setClientConfig(builder.build());
        }

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        addressUri = UnsafeUriCharactersEncoder.encodeHttpURI(addressUri);
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), parameters);
        endpoint.setHttpUri(httpUri);
        
        return endpoint;
    }

    public AsyncHttpClient getClient() {
        return client;
    }

    /**
     * To use a custom {@link AsyncHttpClient}
     */
    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AhcBinding getBinding() {
        if (binding == null) {
            binding = new DefaultAhcBinding();
        }
        return binding;
    }

    /**
     * To use a custom {@link AhcBinding} which allows to control how to bind between AHC and Camel.
     */
    public void setBinding(AhcBinding binding) {
        this.binding = binding;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * To configure the AsyncHttpClient to use a custom com.ning.http.client.AsyncHttpClientConfig instance.
     */
    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Reference to a org.apache.camel.support.jsse.SSLContextParameters in the Registry.
     * Note that configuring this option will override any SSL/TLS configuration options provided through the
     * clientConfig option at the endpoint or component level.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isAllowJavaSerializedObject() {
        return allowJavaSerializedObject;
    }

    /**
     * Whether to allow java serialization when a request uses context-type=application/x-java-serialized-object
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
        this.allowJavaSerializedObject = allowJavaSerializedObject;
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

    protected String createAddressUri(String uri, String remaining) {
        return remaining;
    }

    protected AhcEndpoint createAhcEndpoint(String endpointUri, AhcComponent component, URI httpUri) {
        return new AhcEndpoint(endpointUri, component, httpUri);
    }

    /**
     * Creates a new client configuration builder using {@code DefaultAsyncHttpClientConfig} as a template for
     * the builder.
     *
     * @param clientConfig the instance to serve as a template for the builder
     * @return a builder configured with the same options as the supplied config
     */
    static DefaultAsyncHttpClientConfig.Builder cloneConfig(AsyncHttpClientConfig clientConfig) {
        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder(clientConfig);
        /*
         * The builder creates a new ThreadSafeCookieStore and does not copy the
         * one from clientConfig. This might be desired in case no explicit
         * cookie store was set on the builder that built the clientConfig,
         * because otherwise all endpoints sharing a configuration will also
         * share the cookie store. On the other hand if someone explicitly
         * configured a cookie store (or no cookie store) on the provided
         * config, he likely intends to use it, so we create either a new
         * default implementation or we keep the non default one (or the null
         * value).
         */
        CookieStore cookieStore = clientConfig.getCookieStore();
        if (!(cookieStore instanceof ThreadSafeCookieStore)) {
            builder.setCookieStore(cookieStore);
        }
        return builder;
    }
}
