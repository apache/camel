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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.LoadPropertiesException;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.after;
import static org.apache.camel.util.StringHelper.before;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * An awesome REST endpoint backed by Swagger specifications.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "rest-swagger", title = "REST Swagger",
    syntax = "rest-swagger:operationId:specificationPath", label = "rest,swagger,http", producerOnly = true)
public final class RestSwaggerEndpoint extends DefaultEndpoint {

    private static final BiConsumer<HashMap<String, RestProducerFactory>,
        HashMap<String, RestProducerFactory>> NOOP = (map, component) -> {
        };

    @UriParam(description = "Camel component that will perform the requests", label = "producer")
    private Component delegate;

    @UriParam(description = "Name of the Camel component to use. This component should be added to Camel context and "
        + "should implement the RestProducerFactory SPI. For instance `undertow`, if not defined it is expected that "
        + "single REST producer Camel component will be present on the classpath.", label = "producer")
    private String delegateName;

    @UriParam(description = "Scheme, hostname and optionally port to use, in the form of `http[s]://hostname[:port]`",
        label = "producer")
    private String endpoint;

    @UriPath(description = "ID of the operation from the Swagger specification", label = "producer")
    @Metadata(required = "true")
    private String operationId;

    @UriPath(description = "Path to the Swagger specification file",
        defaultValue = RestSwaggerComponent.DEFAULT_SPECIFICATION_PATH,
        defaultValueNote = "By default loads `swagger.json` file")
    private String specificationPath = RestSwaggerComponent.DEFAULT_SPECIFICATION_PATH;

    public RestSwaggerEndpoint() {
        // help tooling instantiate endpoint
    }

    public RestSwaggerEndpoint(final String uri, final String remaining, final RestSwaggerComponent component) {
        super(notEmpty(uri, "uri"), notNull(component, "component"));

        operationId = Optional.ofNullable(before(remaining, ":")).orElse(remaining);

        final String componentSpecificationPath = component.getSpecificationPath();

        specificationPath = Optional.ofNullable(after(remaining, ":"))//
            .orElse(Optional.ofNullable(componentSpecificationPath)
                .orElse(RestSwaggerComponent.DEFAULT_SPECIFICATION_PATH));

        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public Producer createProducer() throws Exception {
        final CamelContext camelContext = getCamelContext();
        final Swagger swagger = loadSpecificationFrom(camelContext, specificationPath);

        final Map<String, Path> paths = swagger.getPaths();

        for (final Entry<String, Path> pathEntry : paths.entrySet()) {
            final Path path = pathEntry.getValue();

            final Optional<Entry<HttpMethod, Operation>> operation = path.getOperationMap().entrySet().stream()
                .filter(operationEntry -> operationId.equals(operationEntry.getValue().getOperationId())).findAny();

            if (operation.isPresent()) {
                final String uriPath = pathEntry.getKey();
                final String method = operation.get().getKey().name();

                return createProducerFor(method, uriPath);
            }
        }

        final String supportedOperations = paths.values().stream().flatMap(p -> p.getOperations().stream())
            .map(Operation::getOperationId).collect(Collectors.joining(", "));

        throw new IllegalArgumentException("The specified operation with ID: `" + operationId
            + "` cannot be found in the Swagger specification loaded from `" + specificationPath
            + "`. Operations defined in the specification are: " + supportedOperations);
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
     * Returns the configured ID of the operation from Swagger specification.
     *
     * @return the operationId
     */
    public String getOperationId() {
        return operationId;
    }

    /**
     * Returns the configured path to the Swagger specification file.
     *
     * @return the specificationPath
     */
    public String getSpecificationPath() {
        return specificationPath;
    }

    @Override
    public boolean isSingleton() {
        return true;
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
     * The ID of the operation from Swagger specification to use.
     *
     * @param operationId the operationId to set
     */
    public void setOperationId(final String operationId) {
        this.operationId = notEmpty(operationId, "operationId");
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

    Producer createProducerFor(final String method, final String uriPath)
        throws LoadPropertiesException, IOException, URISyntaxException {
        final RestProducerFactory restProducerFactory = determineRestProducerFactory();

        final String endpointToUse = determineEndpoint();

        return new RestSwaggerProducer(this, restProducerFactory, endpointToUse, method, uriPath);
    }

    String determineEndpoint() {
        final RestSwaggerComponent component = (RestSwaggerComponent) getComponent();

        if (isNotEmpty(endpoint)) {
            return endpoint;
        } else {
            final String endpointFromComponent = component.getEndpoint();

            if (isNotEmpty(endpointFromComponent)) {
                return endpointFromComponent;
            } else {
                throw new IllegalArgumentException(
                    "You must specify the API endpoint by setting the `endpoint` property either on the component or on the endpoint.");
            }
        }
    }

    RestProducerFactory determineRestProducerFactory() throws LoadPropertiesException, IOException {
        final RestSwaggerComponent component = (RestSwaggerComponent) getComponent();

        if (delegate != null) {
            return tryUsingSpecifiedDelegate(delegate);
        }

        final CamelContext camelContext = getCamelContext();

        if (isNotEmpty(delegateName)) {
            return tryUsingSpecifiedComponent(camelContext, delegateName);
        }

        final Component delegateFromComponent = component.getDelegate();
        if (delegateFromComponent != null) {
            return tryUsingSpecifiedDelegate(delegateFromComponent);
        }

        final String delegateNameFromComponent = component.getDelegateName();
        if (isNotEmpty(delegateNameFromComponent)) {
            return tryUsingSpecifiedComponent(camelContext, delegateNameFromComponent);
        }

        return tryFindingComponentToUse(camelContext);
    }

    /**
     * Loads the Swagger definition model from the given path. Tries to resolve
     * the resource using Camel's resource loading support, if it fails uses
     * Swagger's resource loading support instead.
     *
     * @param path path to the specification
     * @param camelContext context to use
     * @return the specification
     * @throws IOException
     */
    static Swagger loadSpecificationFrom(final CamelContext camelContext, final String path) throws IOException {
        final ObjectMapper mapper = Json.mapper();

        final SwaggerParser swaggerParser = new SwaggerParser();

        Swagger answer = null;
        final ClassLoader classLoader = camelContext.getApplicationContextClassLoader();
        try (InputStream stream = loadResourceAsStream(path, classLoader)) {
            if (stream != null) {
                final JsonNode node = mapper.readTree(stream);

                answer = swaggerParser.read(node);
            }
        }

        if (answer == null) {
            answer = swaggerParser.read(path);
        }

        return notNull(answer,
            "The given Swagger specification could not be loaded from `" + path
                + "`. Tried loading from classpath and using Swagger's own resource resolution."
                + " Swagger tends to swallow exceptions while parsing, try specifying Java system property `debugParser`"
                + " (e.g. `-DdebugParser=true`)");
    }

    static RestProducerFactory tryFindingComponentToUse(final CamelContext camelContext)
        throws LoadPropertiesException, IOException {
        final Map<String, Properties> foundComponents = camelContext.findComponents();

        final Map<String,
            RestProducerFactory> components = foundComponents.keySet().stream().collect(HashMap::new, (map, name) -> {
                final Component component = camelContext.getComponent(name, true, false);

                if (component instanceof RestProducerFactory) {
                    map.put(name, (RestProducerFactory) component);
                }
            }, RestSwaggerEndpoint.NOOP);

        if (components.isEmpty()) {
            final String knownComponents = String.join("`, `", RestSwaggerComponent.KNOWN_WORKING_COMPONENTS);

            throw new IllegalStateException(
                "Could not find any componenents that are also RestProducerFactory implementations. "
                    + "Try adding one of: `" + knownComponents + "` Camel components to classpath.");
        }

        if (components.size() > 1) {
            final String availableComponents = components.keySet().stream().collect(Collectors.joining(", "));

            throw new IllegalStateException(
                "Found more than one component that is also a RestProducerFactory implementation and the `componentName` "
                    + "property has not been set. Either specify REST producer to use by setting the `componentName` "
                    + "property to the name of the implementation you want to use or leave only one REST producer "
                    + "implementation on the classpath. Currently available REST producers are: " + availableComponents
                    + ".");
        }

        return components.values().iterator().next();
    }

    static RestProducerFactory tryUsingSpecifiedComponent(final CamelContext camelContext, final String componentName) {
        final Component component = camelContext.getComponent(componentName);

        if (component == null) {
            throw new IllegalStateException(
                "The Camel context does not contain the REST producer component specified by the `componentName` property: `"
                    + componentName + "`.");
        }

        if (component instanceof RestProducerFactory) {
            return (RestProducerFactory) component;
        } else {
            throw new IllegalStateException("The component specified by the `componentName` property: `" + componentName
                + "` does not implement RestProducerFactory SPI.");
        }
    }

    static RestProducerFactory tryUsingSpecifiedDelegate(final Component delegate) {
        if (!(delegate instanceof RestProducerFactory)) {
            throw new IllegalStateException("Given delegate component does not implement the RestProducerFactory SPI: `"
                + delegate.getClass().getName() + "`.");
        }

        return (RestProducerFactory) delegate;
    }
}
