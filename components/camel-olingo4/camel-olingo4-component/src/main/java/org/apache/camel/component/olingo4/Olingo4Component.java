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
package org.apache.camel.component.olingo4;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.olingo4.api.impl.Olingo4AppImpl;
import org.apache.camel.component.olingo4.internal.Olingo4ApiCollection;
import org.apache.camel.component.olingo4.internal.Olingo4ApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

/**
 * Represents the component that manages {@link Olingo4Endpoint}.
 */
@Component("olingo4")
public class Olingo4Component extends AbstractApiComponent<Olingo4ApiName, Olingo4Configuration, Olingo4ApiCollection>
        implements SSLContextParametersAware {

    @Metadata
    Olingo4Configuration configuration;

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    // component level shared proxy
    private Olingo4AppWrapper apiProxy;

    public Olingo4Component() {
        super(Olingo4ApiName.class, Olingo4ApiCollection.getCollection());
    }

    public Olingo4Component(CamelContext context) {
        super(context, Olingo4ApiName.class, Olingo4ApiCollection.getCollection());
    }

    @Override
    protected Olingo4ApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(Olingo4ApiName.class, apiNameStr);
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
            parameters.put(Olingo4Endpoint.RESOURCE_PATH_PROPERTY, resourcePath.toString());
        }

        final Olingo4Configuration endpointConfiguration = createEndpointConfiguration(Olingo4ApiName.DEFAULT);
        final Endpoint endpoint = createEndpoint(uri, methodName, Olingo4ApiName.DEFAULT, endpointConfiguration);

        // configure endpoint properties and initialize state
        setProperties(endpoint, parameters);

        return endpoint;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, Olingo4ApiName apiName, Olingo4Configuration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new Olingo4Endpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(Olingo4Configuration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public Olingo4Configuration getConfiguration() {
        return super.getConfiguration();
    }

    public Olingo4AppWrapper createApiProxy(Olingo4Configuration endpointConfiguration) {
        final Olingo4AppWrapper result;
        if (endpointConfiguration.equals(getConfiguration())) {
            synchronized (this) {
                if (apiProxy == null) {
                    apiProxy = createOlingo4App(getConfiguration());
                }
            }
            result = apiProxy;
        } else {
            result = createOlingo4App(endpointConfiguration);
        }
        return result;
    }

    private Olingo4AppWrapper createOlingo4App(Olingo4Configuration configuration) {

        Object clientBuilder = configuration.getHttpAsyncClientBuilder();
        if (clientBuilder == null) {
            HttpAsyncClientBuilder asyncClientBuilder = HttpAsyncClientBuilder.create();

            // apply simple configuration properties
            final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
            requestConfigBuilder.setConnectTimeout(configuration.getConnectTimeout());
            requestConfigBuilder.setSocketTimeout(configuration.getSocketTimeout());

            final HttpHost proxy = configuration.getProxy();
            if (proxy != null) {
                requestConfigBuilder.setProxy(proxy);
            }

            // set default request config
            asyncClientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters == null) {
                // use global ssl config
                sslContextParameters = retrieveGlobalSslContextParameters();
            }
            if (sslContextParameters == null) {
                // use defaults if not specified
                sslContextParameters = new SSLContextParameters();
            }
            try {
                asyncClientBuilder.setSSLContext(sslContextParameters.createSSLContext(getCamelContext()));
            } catch (IOException | GeneralSecurityException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }

            clientBuilder = asyncClientBuilder;
        }

        Olingo4AppImpl olingo4App;
        if (clientBuilder == null || clientBuilder instanceof HttpAsyncClientBuilder) {
            olingo4App = new Olingo4AppImpl(configuration.getServiceUri(), (HttpAsyncClientBuilder) clientBuilder);
        } else {
            olingo4App = new Olingo4AppImpl(configuration.getServiceUri(), (HttpClientBuilder) clientBuilder);
        }
        apiProxy = new Olingo4AppWrapper(olingo4App);
        apiProxy.getOlingo4App().setContentType(configuration.getContentType());
        apiProxy.getOlingo4App().setHttpHeaders(configuration.getHttpHeaders());

        return apiProxy;
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

    public void closeApiProxy(Olingo4AppWrapper apiProxy) {
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
}
