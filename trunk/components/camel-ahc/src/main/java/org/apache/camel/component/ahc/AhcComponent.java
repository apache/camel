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
package org.apache.camel.component.ahc;

import java.net.URI;
import java.util.Map;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.filter.IOExceptionFilter;
import com.ning.http.client.filter.RequestFilter;
import com.ning.http.client.filter.ResponseFilter;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.jsse.SSLContextParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Defines the <a href="http://camel.apache.org/ahc.html">Async HTTP Client Component</a>
 */
public class AhcComponent extends HeaderFilterStrategyComponent {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(AhcComponent.class);
    
    private static final String CLIENT_CONFIG_PREFIX = "clientConfig.";

    private AsyncHttpClient client;
    private AsyncHttpClientConfig clientConfig;
    private AhcBinding binding;
    private SSLContextParameters sslContextParameters;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String addressUri = remaining;

        // Do not set the HTTP URI because we still have all of the Camel internal
        // parameters in the URI at this point.
        AhcEndpoint endpoint = new AhcEndpoint(uri, this, null);
        setEndpointHeaderFilterStrategy(endpoint);
        endpoint.setClient(getClient());
        endpoint.setClientConfig(getClientConfig());
        endpoint.setBinding(getBinding());
        endpoint.setSslContextParameters(getSslContextParameters());
        
        setProperties(endpoint, parameters);
        
        if (IntrospectionSupport.hasProperties(parameters, CLIENT_CONFIG_PREFIX)) {
            AsyncHttpClientConfig.Builder builder = endpoint.getClientConfig() == null 
                    ? new AsyncHttpClientConfig.Builder() : AhcComponent.cloneConfig(endpoint.getClientConfig());
            
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
            
            // set and validate additional parameters on client config
            IntrospectionSupport.setProperties(builder, parameters, CLIENT_CONFIG_PREFIX, true);
            validateParameters(uri, parameters, CLIENT_CONFIG_PREFIX);
            
            endpoint.setClientConfig(builder.build());
        }
        
        // restructure uri to be based on the parameters left as we don't want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(addressUri), CastUtils.cast(parameters));
        endpoint.setHttpUri(httpUri);
        
        return endpoint;
    }

    public AsyncHttpClient getClient() {
        return client;
    }

    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AhcBinding getBinding() {
        if (binding == null) {
            binding = new DefaultAhcBinding();
        }
        return binding;
    }

    public void setBinding(AhcBinding binding) {
        this.binding = binding;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
    
    /**
     * Creates a new client configuration builder using {@code clientConfig} as a template for
     * the builder.
     *
     * @param clientConfig the instance to serve as a template for the builder
     *
     * @return a builder configured with the same options as the supplied config
     */
    static AsyncHttpClientConfig.Builder cloneConfig(AsyncHttpClientConfig clientConfig) {
        
        // TODO - Replace with copy based constructor and remove duplicate copy code below when AHC 1.7 is released (CAMEL-4081).
        AsyncHttpClientConfig.Builder builder =
            new AsyncHttpClientConfig.Builder();
        
        builder.setAllowPoolingConnection(clientConfig.getAllowPoolingConnection());
        builder.setAsyncHttpClientProviderConfig(clientConfig.getAsyncHttpProviderConfig());
        builder.setConnectionsPool(clientConfig.getConnectionsPool());
        builder.setConnectionTimeoutInMs(clientConfig.getConnectionTimeoutInMs());
        builder.setIdleConnectionInPoolTimeoutInMs(clientConfig.getIdleConnectionInPoolTimeoutInMs());
        builder.setMaximumConnectionsPerHost(clientConfig.getMaxConnectionPerHost());
        builder.setMaximumNumberOfRedirects(clientConfig.getMaxRedirects());
        builder.setMaximumConnectionsTotal(clientConfig.getMaxTotalConnections());
        builder.setProxyServer(clientConfig.getProxyServer());
        builder.setRealm(clientConfig.getRealm());
        builder.setRequestTimeoutInMs(clientConfig.getRequestTimeoutInMs());
        builder.setSSLContext(clientConfig.getSSLContext());
        builder.setSSLEngineFactory(clientConfig.getSSLEngineFactory());
        builder.setUserAgent(clientConfig.getUserAgent());
        builder.setFollowRedirects(clientConfig.isRedirectEnabled());
        builder.setCompressionEnabled(clientConfig.isCompressionEnabled());
        builder.setScheduledExecutorService(clientConfig.reaper());
        builder.setExecutorService(clientConfig.executorService());

        for (RequestFilter filter : clientConfig.getRequestFilters()) {
            builder.addRequestFilter(filter);
        }
        
        for (ResponseFilter filter : clientConfig.getResponseFilters()) {
            builder.addResponseFilter(filter);
        }

        for (IOExceptionFilter filter : clientConfig.getIOExceptionFilters()) {
            builder.addIOExceptionFilter(filter);
        }
        
        builder.setRequestCompressionLevel(clientConfig.getRequestCompressionLevel());
        builder.setUseRawUrl(clientConfig.isUseRawUrl());
        builder.setMaxRequestRetry(clientConfig.getMaxRequestRetry());
        builder.setAllowSslConnectionPool(clientConfig.getAllowPoolingConnection());
        // End of duplicate code to remove.
        
        return builder;
    }
}
