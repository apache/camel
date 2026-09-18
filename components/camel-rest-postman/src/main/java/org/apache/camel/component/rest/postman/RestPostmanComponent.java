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
package org.apache.camel.component.rest.postman;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.rest.postman.collection.PostmanCollectionCache;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.PropertiesHelper;

/**
 * A REST component backed by Postman Collections.
 * <p>
 * Endpoints created by this component connect to the REST APIs described by a Postman Collection, either invoking one
 * request of the collection, running a whole folder or collection in the manner of Postman's collection runner, or
 * servicing the collection's requests as a contract-first HTTP server.
 * <p>
 * Example usage in the Java DSL:
 *
 * <pre>
 * from(...).to("rest-postman:petstore.json#getPetById")
 * </pre>
 * <p>
 * The collection can also be fetched from the Postman cloud by its uid, which needs a Postman API key. Note that this
 * key authenticates against Postman in order to download the collection; it is never sent to the API that the
 * collection describes:
 *
 * <pre>
 * from(...).to("rest-postman:12ece9e1-2abf-4edc-8e34-de66e74114d2#getPetById?postmanApiKey=PMAK-...")
 * </pre>
 */
@Component("rest-postman")
public class RestPostmanComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(description = "The Postman Collection to use, when it is not given on the endpoint. Either a resource"
                            + " URI of a Collection v2.1 JSON document (classpath:, file: or http:), or the uid of a"
                            + " collection to fetch from the Postman cloud.",
              label = "common")
    private String collectionSource;

    @Metadata(label = "advanced")
    private RestPostmanConfiguration configuration = new RestPostmanConfiguration();

    @Metadata(description = "To use a custom strategy for how to service the requests of the collection.",
              label = "consumer,advanced")
    private RestPostmanProcessorStrategy restPostmanProcessorStrategy;

    private final PostmanCollectionCache collectionCache = new PostmanCollectionCache();

    public RestPostmanComponent() {
    }

    public RestPostmanComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // the endpoint is lenient, so anything left in parameters becomes a literal path or query value. The
        // multi-value variable. prefix therefore has to be taken out explicitly, or every variable would also be
        // sent as a bogus query parameter
        Map<String, Object> variables = PropertiesHelper.extractProperties(parameters, "variable.");

        RestPostmanEndpoint endpoint = new RestPostmanEndpoint(uri, remaining, this, parameters);
        endpoint.setConfiguration(configuration.copy());
        setProperties(endpoint, parameters);

        if (!variables.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (endpoint.getConfiguration().getVariables() != null) {
                merged.putAll(endpoint.getConfiguration().getVariables());
            }
            merged.putAll(variables);
            endpoint.getConfiguration().setVariables(merged);
        }
        return endpoint;
    }

    /**
     * Creates the strategy an endpoint's consumer should use: the configured one when given, otherwise a fresh default
     * per endpoint, since the strategy holds per-endpoint state.
     */
    RestPostmanProcessorStrategy createProcessorStrategy() {
        if (restPostmanProcessorStrategy != null) {
            return restPostmanProcessorStrategy;
        }
        DefaultRestPostmanProcessorStrategy strategy = new DefaultRestPostmanProcessorStrategy();
        CamelContextAware.trySetCamelContext(strategy, getCamelContext());
        strategy.setMissingRequest(configuration.getMissingRequest());
        strategy.setMockIncludePattern(configuration.getMockIncludePattern());
        return strategy;
    }

    PostmanCollectionCache getCollectionCache() {
        return collectionCache;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        collectionCache.clear();
    }

    public String getCollectionSource() {
        return collectionSource;
    }

    public void setCollectionSource(String collectionSource) {
        this.collectionSource = collectionSource;
    }

    public RestPostmanConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The shared configuration used as the template for every endpoint created by this component.
     */
    public void setConfiguration(RestPostmanConfiguration configuration) {
        this.configuration = configuration;
    }

    public RestPostmanProcessorStrategy getRestPostmanProcessorStrategy() {
        return restPostmanProcessorStrategy;
    }

    public void setRestPostmanProcessorStrategy(RestPostmanProcessorStrategy restPostmanProcessorStrategy) {
        this.restPostmanProcessorStrategy = restPostmanProcessorStrategy;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return configuration.isUseGlobalSslContextParameters();
    }

    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        configuration.setUseGlobalSslContextParameters(useGlobalSslContextParameters);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
    }
}
