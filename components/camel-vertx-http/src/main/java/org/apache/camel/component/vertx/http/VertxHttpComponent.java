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
package org.apache.camel.component.vertx.http;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.support.RestProducerFactoryHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

@Component("vertx-http")
public class VertxHttpComponent extends HeaderFilterStrategyComponent implements RestProducerFactory, SSLContextParametersAware {

    @Metadata(label = "advanced")
    private Vertx vertx;
    @Metadata(label = "advanced")
    private VertxOptions vertxOptions;
    @Metadata(label = "advanced")
    private VertxHttpBinding vertxHttpBinding;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced")
    private boolean allowJavaSerializedObject;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        VertxHttpConfiguration configuration = new VertxHttpConfiguration();

        URI uriHttpUriAddress = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));

        VertxHttpEndpoint endpoint = new VertxHttpEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        if (configuration.getVertxHttpBinding() == null) {
            configuration.setVertxHttpBinding(getVertxHttpBinding());
        }

        if (configuration.getHeaderFilterStrategy() == null) {
            configuration.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }

        // Recreate the http uri with the remaining parameters which the endpoint did not use
        URI httpUri = URISupport.createRemainingURI(
                new URI(uriHttpUriAddress.getScheme(),
                        uriHttpUriAddress.getUserInfo(),
                        uriHttpUriAddress.getHost(),
                        uriHttpUriAddress.getPort(),
                        uriHttpUriAddress.getPath(),
                        uriHttpUriAddress.getQuery(),
                        uriHttpUriAddress.getFragment()),
                        parameters);

        configuration.setHttpUri(httpUri);

        return endpoint;
    }

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
            String verb, String basePath, String uriTemplate, String queryParameters, String consumes,
            String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // get the endpoint
        String scheme = "vertx-http:";
        String url = scheme + host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }

        RestConfiguration config = configuration;
        if (config == null) {
            config = CamelContextHelper.getRestConfiguration(getCamelContext(), null, scheme);
        }

        Map<String, Object> map = new HashMap<>();
        // build query string, and append any endpoint configuration properties
        if (config.getProducerComponent() == null || config.getProducerComponent().equals(scheme)) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        // get the endpoint
        String query = URISupport.createQueryString(map);
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }

        parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

        // there are cases where we might end up here without component being created beforehand
        // we need to abide by the component properties specified in the parameters when creating
        // the component, one such case is when we switch from "http" to "https" component name
        RestProducerFactoryHelper.setupComponentFor(url, camelContext, (Map<String, Object>) parameters.remove("component"));

        VertxHttpEndpoint endpoint = camelContext.getEndpoint(url, VertxHttpEndpoint.class);
        setProperties(endpoint, parameters);
        String path = uriTemplate != null ? uriTemplate : basePath;
        endpoint.getConfiguration().setHeaderFilterStrategy(new VertxHttpRestHeaderFilterStrategy(path, queryParameters));

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return endpoint.createProducer();
    }

    public Vertx getVertx() {
        if (vertx == null) {
            Set<Vertx> vertxes = getCamelContext().getRegistry().findByType(Vertx.class);
            if (vertxes.size() == 1) {
                vertx  = vertxes.iterator().next();
            }
        }

        if (vertx == null) {
            if (vertxOptions != null) {
                vertx = Vertx.vertx(vertxOptions);
            } else {
                vertx = Vertx.vertx();
            }
        }

        return vertx;
    }

    /**
     * To use an existing vertx instead of creating a new instance
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public VertxOptions getVertxOptions() {
        return vertxOptions;
    }

    /**
     * To provide a custom set of vertx options for configuring vertx
     */
    public void setVertxOptions(VertxOptions vertxOptions) {
        this.vertxOptions = vertxOptions;
    }

    public VertxHttpBinding getVertxHttpBinding() {
        if (vertxHttpBinding == null) {
            vertxHttpBinding = new DefaultVertxHttpBinding();
        }
        return vertxHttpBinding;
    }

    /**
     * A custom VertxHttpBinding which can control how to bind between Vert.x and Camel
     */
    public void setVertxHttpBinding(VertxHttpBinding vertxHttpBinding) {
        this.vertxHttpBinding = vertxHttpBinding;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public boolean isAllowJavaSerializedObject() {
        return allowJavaSerializedObject;
    }

    /**
     * Whether to allow java serialization when a request has the Content-Type application/x-java-serialized-object
     * <p/>
     * This is disabled by default. If you enable this, be aware that Java will deserialize the incoming
     * data from the request. This can be a potential security risk.
     */
    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
        this.allowJavaSerializedObject = allowJavaSerializedObject;
    }
}
