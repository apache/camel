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
package org.apache.camel.component.olingo2;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl;
import org.apache.camel.component.olingo2.internal.Olingo2ApiCollection;
import org.apache.camel.component.olingo2.internal.Olingo2ApiName;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.AbstractApiComponent;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

/**
 * Represents the component that manages {@link Olingo2Endpoint}.
 */
public class Olingo2Component extends AbstractApiComponent<Olingo2ApiName, Olingo2Configuration, Olingo2ApiCollection> {

    // component level shared proxy
    private Olingo2AppWrapper apiProxy;

    public Olingo2Component() {
        super(Olingo2Endpoint.class, Olingo2ApiName.class, Olingo2ApiCollection.getCollection());
    }

    public Olingo2Component(CamelContext context) {
        super(context, Olingo2Endpoint.class, Olingo2ApiName.class, Olingo2ApiCollection.getCollection());
    }

    @Override
    protected Olingo2ApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return Olingo2ApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // parse remaining to extract resourcePath and queryParams
        final String[] pathSegments = remaining.split("/", -1);
        final String methodName = pathSegments[0];

        if (pathSegments.length > 1) {
            final StringBuilder resourcePath = new StringBuilder();
            for (int i = 1; i < pathSegments.length; i++) {
                resourcePath.append(pathSegments[i]);
                if (i < (pathSegments.length - 1)) {
                    resourcePath.append('/');
                }
            }
            // This will override any URI supplied ?resourcePath=... param
            parameters.put(Olingo2Endpoint.RESOURCE_PATH_PROPERTY, resourcePath.toString());
        }

        final Olingo2Configuration endpointConfiguration = createEndpointConfiguration(Olingo2ApiName.DEFAULT);
        final Endpoint endpoint = createEndpoint(uri, methodName, Olingo2ApiName.DEFAULT, endpointConfiguration);

        // set endpoint property inBody
        setProperties(endpoint, parameters);

        // configure endpoint properties and initialize state
        endpoint.configureProperties(parameters);

        return endpoint;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, Olingo2ApiName apiName,
                                      Olingo2Configuration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new Olingo2Endpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public void setConfiguration(Olingo2Configuration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public Olingo2Configuration getConfiguration() {
        return super.getConfiguration();
    }

    public Olingo2AppWrapper createApiProxy(Olingo2Configuration endpointConfiguration) {
        final Olingo2AppWrapper result;
        if (endpointConfiguration.equals(this.configuration)) {
            synchronized (this) {
                if (apiProxy == null) {
                    apiProxy = createOlingo2App(this.configuration);
                }
            }
            result = apiProxy;
        } else {
            result = createOlingo2App(endpointConfiguration);
        }
        return result;
    }

    private Olingo2AppWrapper createOlingo2App(Olingo2Configuration configuration) {

        HttpAsyncClientBuilder clientBuilder = configuration.getHttpAsyncClientBuilder();
        if (clientBuilder == null) {
            clientBuilder = HttpAsyncClientBuilder.create();

            // apply simple configuration properties
            final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
            requestConfigBuilder.setConnectTimeout(configuration.getConnectTimeout());
            requestConfigBuilder.setSocketTimeout(configuration.getSocketTimeout());

            final HttpHost proxy = configuration.getProxy();
            if (proxy != null) {
                requestConfigBuilder.setProxy(proxy);
            }

            // set default request config
            clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters == null) {
                // use defaults if not specified
                sslContextParameters = new SSLContextParameters();
            }
            try {
                clientBuilder.setSSLContext(sslContextParameters.createSSLContext(getCamelContext()));
            } catch (GeneralSecurityException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            } catch (IOException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        apiProxy = new Olingo2AppWrapper(new Olingo2AppImpl(configuration.getServiceUri(), clientBuilder));
        apiProxy.getOlingo2App().setContentType(configuration.getContentType());
        apiProxy.getOlingo2App().setHttpHeaders(configuration.getHttpHeaders());

        return apiProxy;
    }

    public void closeApiProxy(Olingo2AppWrapper apiProxy) {
        if (this.apiProxy != apiProxy) {
            // not a shared proxy
            apiProxy.close();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (apiProxy != null) {
            apiProxy.close();
        }
    }

    private Olingo2Configuration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new Olingo2Configuration());
        }
        return this.getConfiguration();
    }

    public Olingo2ApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(Olingo2ApiName apiName) {
        getConfigurationOrCreate().setApiName(apiName);
    }

    public String getMethodName() {
        return getConfigurationOrCreate().getMethodName();
    }

    /**
     * What sub operation to use for the selected operation
     * @param methodName
     */
    public void setMethodName(String methodName) {
        getConfigurationOrCreate().setMethodName(methodName);
    }

    public String getServiceUri() {
        return getConfigurationOrCreate().getServiceUri();
    }

    /**
     * Target OData service base URI, e.g. http://services.odata.org/OData/OData.svc
     * @param serviceUri
     */
    public void setServiceUri(String serviceUri) {
        getConfigurationOrCreate().setServiceUri(serviceUri);
    }

    public String getContentType() {
        return getConfigurationOrCreate().getContentType();
    }

    /**
     * Content-Type header value can be used to specify JSON or XML message format, defaults to application/json;charset=utf-8
     * @param contentType
     */
    public void setContentType(String contentType) {
        getConfigurationOrCreate().setContentType(contentType);
    }

    public Map<String, String> getHttpHeaders() {
        return getConfigurationOrCreate().getHttpHeaders();
    }

    /**
     * Custom HTTP headers to inject into every request, this could include OAuth tokens, etc.
     * @param httpHeaders
     */
    public void setHttpHeaders(Map<String, String> httpHeaders) {
        getConfigurationOrCreate().setHttpHeaders(httpHeaders);
    }

    public int getConnectTimeout() {
        return getConfigurationOrCreate().getConnectTimeout();
    }

    /**
     * HTTP connection creation timeout in milliseconds, defaults to 30,000 (30 seconds)
     * @param connectTimeout
     */
    public void setConnectTimeout(int connectTimeout) {
        getConfigurationOrCreate().setConnectTimeout(connectTimeout);
    }

    public int getSocketTimeout() {
        return getConfigurationOrCreate().getSocketTimeout();
    }

    /**
     * HTTP request timeout in milliseconds, defaults to 30,000 (30 seconds)
     * @param socketTimeout
     */
    public void setSocketTimeout(int socketTimeout) {
        getConfigurationOrCreate().setSocketTimeout(socketTimeout);
    }

    public HttpHost getProxy() {
        return getConfigurationOrCreate().getProxy();
    }

    /**
     * HTTP proxy server configuration
     * @param proxy
     */
    public void setProxy(HttpHost proxy) {
        getConfigurationOrCreate().setProxy(proxy);
    }

    public SSLContextParameters getSslContextParameters() {
        return getConfigurationOrCreate().getSslContextParameters();
    }

    /**
     * To configure security using SSLContextParameters
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        getConfigurationOrCreate().setSslContextParameters(sslContextParameters);
    }

    public HttpAsyncClientBuilder getHttpAsyncClientBuilder() {
        return getConfigurationOrCreate().getHttpAsyncClientBuilder();
    }

    /**
     * Custom HTTP async client builder for more complex HTTP client configuration, overrides connectionTimeout, socketTimeout, proxy and sslContext.
     * Note that a socketTimeout MUST be specified in the builder, otherwise OData requests could block indefinitely
     * @param httpAsyncClientBuilder
     */
    public void setHttpAsyncClientBuilder(HttpAsyncClientBuilder httpAsyncClientBuilder) {
        getConfigurationOrCreate().setHttpAsyncClientBuilder(httpAsyncClientBuilder);
    }
}
