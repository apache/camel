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
package org.apache.camel.component.rest.swagger;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * An awesome REST component backed by Swagger specifications. Creates endpoints
 * that connect to REST APIs defined by Swagger specification. This component
 * delegates to other {@link RestProducerFactory} components to act as REST
 * clients, but it configures them from Swagger specification. Client needs to
 * point to operation that it wants to invoke via REST, provide any additional
 * HTTP headers as headers , and payload as the body of the incoming message.
 * <p>
 * Example usage using Java DSL:
 * <p>
 *
 * <pre>
 * from("rest-swagger:getPetById?endpoint=https://api.petstore.com").to(...)
 * </pre>
 *
 * This relies on only one {@link RestProducerFactory} component being available
 * to Camel, you can use specific, for instance preconfigured component by using
 * the {@code componentName} endpoint property. For example using Undertow
 * component in Java DSL:
 * <p>
 *
 * <pre>
 * Component petstore = new UndertowComponent();
 * petstore.setSslContextParameters(...);
 * //...
 * camelContext.addComponent("petstore", petstore);
 *
 * from("rest-swagger:getPetById?endpoint=https://api.petstore.com&componentName=petstore").to(...)
 * </pre>
 */
public final class RestSwaggerComponent extends DefaultComponent {
    static final String DEFAULT_SPECIFICATION_PATH = "swagger.json";

    static final String[] KNOWN_WORKING_COMPONENTS = new String[] {"http", "http4", "netty4-http", "restlet", "jetty",
        "undertow"};

    @Metadata(description = "Camel component that will perform the requests", label = "producer")
    private Component delegate;

    @Metadata(description = "Name of the Camel component that will perform the requests", label = "producer")
    private String delegateName;

    @Metadata(description = "Scheme, hostname and optionally port to use, in the form of `http[s]://hostname[:port]`",
        label = "producer")
    private String endpoint;

    @UriPath(description = "Path to the Swagger specification file",
        defaultValue = RestSwaggerComponent.DEFAULT_SPECIFICATION_PATH,
        defaultValueNote = "By default loads `swagger.json` file")
    private String specificationPath;

    public RestSwaggerComponent() {
    }

    public RestSwaggerComponent(final CamelContext context) {
        super(context);
    }

    /**
     * Returns {@link Component} that this component delegates to.
     *
     * @return the delegate
     */
    public Component getDelegate() {
        return delegate;
    }

    /**
     * The name of the {@link Component} that this component delegates to.
     *
     * @return the delegateName name of the delegate
     */
    public String getDelegateName() {
        return delegateName;
    }

    /**
     * Returns the configured endpoint.
     *
     * @return the host
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Returns the configured path to the Swagger specification file.
     *
     * @return the specificationPath
     */
    public String getSpecificationPath() {
        return specificationPath;
    }

    /**
     * The {@link Component} that this component will delegate to. The component
     * must implement {@link RestProducerFactory} SPI interface.
     *
     * @param delegate the delegate to set
     *
     * @see RestSwaggerComponent#KNOWN_WORKING_COMPONENTS
     */
    public void setDelegate(final Component delegate) {
        this.delegate = notNull(delegate, "delegate");
    }

    /**
     * Name of the {@link Component} to delegate the requests to, the component
     * should be added to {@link CamelContext}. The component must implement
     * {@link RestProducerFactory} SPI interface.
     *
     * @param delegateName name of the delegate component
     *
     * @see RestSwaggerComponent#KNOWN_WORKING_COMPONENTS
     */
    public void setDelegateName(final String delegateName) {
        this.delegateName = notEmpty(delegateName, "delegateName");
    }

    /**
     * The scheme, host and optionally port number to use in the syntax
     * {@code http[s]://hostname[:port]}. For example
     * {@code https://api.example.com}.
     *
     * @param endpoint host to set
     */
    public void setEndpoint(final String endpoint) {
        this.endpoint = notEmpty(endpoint, "endpoint");
    }

    /**
     * Path to the Swagger specification file. Searched within CLASSPATH first
     * and if not found delegated to Swaggers resource loading support.
     *
     * @param specificationPath the specificationPath to set
     */
    public void setSpecificationPath(final String specificationPath) {
        this.specificationPath = notEmpty(specificationPath, "specificationPath");
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
        throws Exception {
        final Endpoint endpoint = new RestSwaggerEndpoint(uri, remaining, this);

        setProperties(endpoint, parameters);

        return endpoint;
    }

}
