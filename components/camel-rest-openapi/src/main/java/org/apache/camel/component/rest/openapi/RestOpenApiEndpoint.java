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

package org.apache.camel.component.rest.openapi;

import static org.apache.camel.component.rest.openapi.RestOpenApiHelper.isHostParam;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.StringHelper.after;
import static org.apache.camel.util.StringHelper.before;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.component.rest.openapi.validator.DefaultRequestValidator;
import org.apache.camel.component.rest.openapi.validator.RequestValidator;
import org.apache.camel.component.rest.openapi.validator.RestOpenApiOperation;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestOpenApiConsumerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To call REST services using OpenAPI specification as contract.
 */
@UriEndpoint(
        firstVersion = "3.1.0",
        scheme = "rest-openapi",
        title = "REST OpenApi",
        syntax = "rest-openapi:specificationUri#operationId",
        category = {Category.REST, Category.API})
public final class RestOpenApiEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiEndpoint.class);

    public static final String[] DEFAULT_REST_OPENAPI_CONSUMER_COMPONENTS = new String[] {"platform-http"};

    /**
     * Remaining parameters specified in the Endpoint URI.
     */
    Map<String, Object> parameters = Collections.emptyMap();

    @UriParam(
            description = "API basePath, for example \"`/v3`\". Default is unset, if set overrides the value present in"
                    + " OpenApi specification and in the component configuration.",
            label = "common")
    private String basePath;

    @UriParam(
            description = "Name of the Camel component that will perform the requests. The component must be present"
                    + " in Camel registry and it must implement RestProducerFactory service provider interface. If not set"
                    + " CLASSPATH is searched for single component that implements RestProducerFactory SPI. Overrides"
                    + " component configuration.",
            label = "producer,advanced")
    private String componentName;

    @UriParam(
            description = "Name of the Camel component that will service the requests. The component must be present"
                    + " in Camel registry and it must implement RestOpenApiConsumerFactory service provider interface. If not set"
                    + " CLASSPATH is searched for single component that implements RestOpenApiConsumerFactory SPI. Overrides"
                    + " component configuration.",
            label = "consumer,advanced")
    private String consumerComponentName;

    @UriParam(
            description = "Scheme hostname and port to direct the HTTP requests to in the form of"
                    + " `http[s]://hostname[:port]`. Can be configured at the endpoint, component or in the corresponding"
                    + " REST configuration in the Camel Context. If you give this component a name (e.g. `petstore`) that"
                    + " REST configuration is consulted first, `rest-openapi` next, and global configuration last. If set"
                    + " overrides any value found in the OpenApi specification, RestConfiguration. Overrides all other "
                    + " configuration.",
            label = "producer")
    private String host;

    @UriPath(
            description = "ID of the operation from the OpenApi specification. This is required when using producer",
            label = "producer")
    private String operationId;

    @UriParam(
            description =
                    "What payload type this component capable of consuming. Could be one type, like `application/json`"
                            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
                            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
                            + " to the value of `Accept` HTTP header. If set overrides any value found in the OpenApi specification and."
                            + " in the component configuration",
            label = "consumer")
    private String consumes;

    @UriParam(
            description = "What payload type this component is producing. For example `application/json`"
                    + " according to the RFC7231. This equates to the value of `Content-Type` HTTP header. If set overrides"
                    + " any value present in the OpenApi specification. Overrides all other configuration.",
            label = "producer")
    private String produces;

    @UriPath(
            description = "Path to the OpenApi specification file. The scheme, host base path are taken from this"
                    + " specification, but these can be overridden with properties on the component or endpoint level. If not"
                    + " given the component tries to load `openapi.json` resource from the classpath. Note that the `host` defined on the"
                    + " component and endpoint of this Component should contain the scheme, hostname and optionally the"
                    + " port in the URI syntax (i.e. `http://api.example.com:8080`). Overrides component configuration."
                    + " The OpenApi specification can be loaded from different sources by prefixing with file: classpath: http: https:."
                    + " Support for https is limited to using the JDK installed UrlHandler, and as such it can be cumbersome to setup"
                    + " TLS/SSL certificates for https (such as setting a number of javax.net.ssl JVM system properties)."
                    + " How to do that consult the JDK documentation for UrlHandler.",
            defaultValue = RestOpenApiComponent.DEFAULT_SPECIFICATION_URI,
            defaultValueNote = "By default loads `openapi.json` file",
            label = "common")
    private String specificationUri;

    @Metadata(
            label = "consumer,advanced",
            description =
                    "Package name to use as base (offset) for classpath scanning of POJO classes are located when using binding mode is enabled for JSon or XML. Multiple package names can be separated by comma.")
    private String bindingPackageScan;

    @UriParam(
            label = "consumer",
            description =
                    "Whether to enable validation of the client request to check if the incoming request is valid according to the OpenAPI specification")
    private boolean clientRequestValidation;

    @UriParam(
            label = "consumer",
            description =
                    "Whether to enable validation of the client request to check if the outgoing response from Camel is valid according to the OpenAPI specification")
    private boolean clientResponseValidation;

    @UriParam(
            label = "producer",
            description = "Enable validation of requests against the configured OpenAPI specification")
    private boolean requestValidationEnabled;

    @UriParam(
            description = "To use a custom strategy for how to process Rest DSL requests",
            label = "consumer,advanced")
    private RestOpenapiProcessorStrategy restOpenapiProcessorStrategy;

    @UriParam(
            description =
                    "Whether the consumer should fail,ignore or return a mock response for OpenAPI operations that are not mapped to a corresponding route.",
            enums = "fail,ignore,mock",
            label = "consumer",
            defaultValue = "fail")
    private String missingOperation;

    @UriParam(
            description =
                    "Used for inclusive filtering of mock data from directories. The pattern is using Ant-path style pattern."
                            + " Multiple patterns can be specified separated by comma.",
            label = "consumer,advanced",
            defaultValue = "classpath:camel-mock/**")
    private String mockIncludePattern;

    @UriParam(label = "consumer", description = "Sets the context-path to use for servicing the OpenAPI specification")
    private String apiContextPath;

    public RestOpenApiEndpoint() {
        // help tooling instantiate endpoint
    }

    public RestOpenApiEndpoint(
            final String uri,
            final String remaining,
            final RestOpenApiComponent component,
            final Map<String, Object> parameters) {
        super(uri, component);
        if (remaining.contains("#")) {
            operationId = after(remaining, "#");
            String spec = before(remaining, "#");
            if (spec != null && !spec.isEmpty()) {
                specificationUri = spec;
            }
        } else {
            if (remaining.endsWith(".json") || remaining.endsWith(".yaml") || remaining.endsWith(".yml")) {
                specificationUri = remaining;
            } else {
                operationId = remaining;
            }
        }
        if (specificationUri == null) {
            specificationUri = component.getSpecificationUri();
        }
        if (specificationUri == null) {
            specificationUri = RestOpenApiComponent.DEFAULT_SPECIFICATION_URI;
        }
        this.parameters = parameters;
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public RestOpenApiComponent getComponent() {
        return (RestOpenApiComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        OpenAPI doc = loadSpecificationFrom(getCamelContext(), specificationUri);
        String path = determineBasePath(doc);

        RestOpenApiProcessor openApiProcessor =
                new RestOpenApiProcessor(this, doc, path, apiContextPath, restOpenapiProcessorStrategy);
        CamelContextAware.trySetCamelContext(openApiProcessor, getCamelContext());

        // use an advice to call the processor that is responsible for routing to the route that matches the
        // operation id, and also do validation of the incoming request
        // the camel route is invoked AFTER the rest-dsl is complete
        if (processor instanceof InternalProcessor ip) {
            // remove existing rest binding advice because RestOpenApiProcessorAdvice has its own binding
            RestBindingAdvice advice = ip.getAdvice(RestBindingAdvice.class);
            if (advice != null) {
                ip.removeAdvice(advice);
            }
            ip.addAdvice(new RestOpenApiProcessorAdvice(openApiProcessor));
        }

        Consumer consumer = createConsumerFor(path, openApiProcessor, processor);
        openApiProcessor.setConsumer(consumer);
        if (consumer instanceof PlatformHttpConsumerAware phca) {
            phca.registerAfterConfigured(openApiProcessor);
        }
        return consumer;
    }

    private Consumer createConsumerFor(String basePath, RestOpenApiProcessor openApiProcessor, Processor processor)
            throws Exception {
        RestOpenApiConsumerFactory factory = null;
        String cname = null;
        if (getConsumerComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(getConsumerComponentName());
            if (comp instanceof RestOpenApiConsumerFactory) {
                factory = (RestOpenApiConsumerFactory) comp;
            } else {
                comp = getCamelContext().getComponent(getConsumerComponentName());
                if (comp instanceof RestOpenApiConsumerFactory) {
                    factory = (RestOpenApiConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException(
                            "Component " + getConsumerComponentName() + " is not a RestOpenApiConsumerFactory");
                } else {
                    throw new NoSuchBeanException(
                            getConsumerComponentName(), RestOpenApiConsumerFactory.class.getName());
                }
            }
            cname = getConsumerComponentName();
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp instanceof RestOpenApiConsumerFactory) {
                    factory = (RestOpenApiConsumerFactory) comp;
                    cname = name;
                    break;
                }
            }
        }

        // favour using platform-http if available on classpath
        if (factory == null) {
            Object comp = getCamelContext().getComponent("platform-http", true);
            if (comp instanceof RestOpenApiConsumerFactory) {
                factory = (RestOpenApiConsumerFactory) comp;
                LOG.debug("Auto discovered platform-http as RestConsumerFactory");
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestOpenApiConsumerFactory> factories =
                    getCamelContext().getRegistry().findByType(RestOpenApiConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        // no explicit factory found then try to see if we can find any of the default rest consumer components
        // and there must only be exactly one so we safely can pick this one
        if (factory == null) {
            RestOpenApiConsumerFactory found = null;
            String foundName = null;
            for (String name : DEFAULT_REST_OPENAPI_CONSUMER_COMPONENTS) {
                Object comp = getCamelContext().getComponent(name, true);
                if (comp instanceof RestOpenApiConsumerFactory) {
                    if (found == null) {
                        found = (RestOpenApiConsumerFactory) comp;
                        foundName = name;
                    } else {
                        throw new IllegalArgumentException(
                                "Multiple RestOpenApiConsumerFactory found on classpath. Configure explicit which component to use");
                    }
                }
            }
            if (found != null) {
                LOG.debug("Auto discovered {} as RestOpenApiConsumerFactory", foundName);
                factory = found;
            }
        }

        if (factory != null) {
            RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), cname);
            Map<String, Object> copy = new HashMap<>(parameters); // defensive copy of the parameters
            // avoid duplicate context-path
            if (basePath.equals(config.getContextPath())) {
                basePath = "";
            }
            Consumer consumer = factory.createConsumer(getCamelContext(), processor, basePath, config, copy);
            if (consumer instanceof PlatformHttpConsumerAware phca) {
                openApiProcessor.setPlatformHttpConsumer(phca);
            }
            configureConsumer(consumer);
            return consumer;
        } else {
            throw new IllegalStateException(
                    "Cannot find RestOpenApiConsumerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        final CamelContext camelContext = getCamelContext();
        final OpenAPI openapiDoc = loadSpecificationFrom(camelContext, specificationUri);
        final Paths paths = openapiDoc.getPaths();

        for (final Entry<String, PathItem> pathEntry : paths.entrySet()) {
            final PathItem path = pathEntry.getValue();
            Map<PathItem.HttpMethod, Operation> operationMap = path.readOperationsMap();
            final Optional<Entry<PathItem.HttpMethod, Operation>> maybeOperationEntry = operationMap.entrySet().stream()
                    .filter(operationEntry ->
                            operationId.equals(operationEntry.getValue().getOperationId()))
                    .findAny();

            if (maybeOperationEntry.isPresent()) {
                final Entry<PathItem.HttpMethod, Operation> operationEntry = maybeOperationEntry.get();
                final Operation operation = operationEntry.getValue();
                Map<String, Parameter> pathParameters;
                if (operation.getParameters() != null) {
                    pathParameters = operation.getParameters().stream()
                            .filter(p -> "path".equals(p.getIn()))
                            .collect(Collectors.toMap(Parameter::getName, Function.identity()));
                } else {
                    pathParameters = new HashMap<>();
                }
                final String uriTemplate = resolveUri(pathEntry.getKey(), pathParameters);
                final HttpMethod httpMethod = operationEntry.getKey();
                final String method = httpMethod.name();
                return createProducerFor(openapiDoc, operation, method, uriTemplate);
            }
        }

        final String supportedOperations = paths.values().stream()
                .flatMap(p -> p.readOperations().stream())
                .map(Operation::getOperationId)
                .collect(Collectors.joining(", "));

        throw new IllegalArgumentException("The specified operation with ID: `" + operationId
                + "` cannot be found in the OpenApi specification loaded from `" + specificationUri
                + "`. Operations defined in the specification are: " + supportedOperations);
    }

    public String getBasePath() {
        return basePath;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    public String getConsumes() {
        return consumes;
    }

    public String getHost() {
        return host;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getProduces() {
        return produces;
    }

    public String getSpecificationUri() {
        return specificationUri;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    public void setBasePath(final String basePath) {
        this.basePath = basePath;
    }

    public void setComponentName(final String componentName) {
        this.componentName = componentName;
    }

    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

    public void setConsumes(final String consumes) {
        this.consumes = consumes;
    }

    public void setHost(final String host) {
        this.host = isHostParam(host);
    }

    public void setOperationId(final String operationId) {
        this.operationId = operationId;
    }

    public void setProduces(final String produces) {
        this.produces = produces;
    }

    public void setSpecificationUri(String specificationUri) {
        this.specificationUri = specificationUri;
    }

    public void setRequestValidationEnabled(boolean requestValidationEnabled) {
        this.requestValidationEnabled = requestValidationEnabled;
    }

    public boolean isRequestValidationEnabled() {
        return requestValidationEnabled;
    }

    public boolean isClientRequestValidation() {
        return clientRequestValidation;
    }

    public void setClientRequestValidation(boolean clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public boolean isClientResponseValidation() {
        return clientResponseValidation;
    }

    public void setClientResponseValidation(boolean clientResponseValidation) {
        this.clientResponseValidation = clientResponseValidation;
    }

    public RestOpenapiProcessorStrategy getRestOpenapiProcessorStrategy() {
        return restOpenapiProcessorStrategy;
    }

    public void setRestOpenapiProcessorStrategy(RestOpenapiProcessorStrategy restOpenapiProcessorStrategy) {
        this.restOpenapiProcessorStrategy = restOpenapiProcessorStrategy;
    }

    public String getMissingOperation() {
        return missingOperation;
    }

    public void setMissingOperation(String missingOperation) {
        this.missingOperation = missingOperation;
    }

    public void setMockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
    }

    public String getMockIncludePattern() {
        return mockIncludePattern;
    }

    public String getApiContextPath() {
        return apiContextPath;
    }

    public void setApiContextPath(String apiContextPath) {
        this.apiContextPath = apiContextPath;
    }

    public String getBindingPackageScan() {
        return bindingPackageScan;
    }

    public void setBindingPackageScan(String bindingPackageScan) {
        this.bindingPackageScan = bindingPackageScan;
    }

    Producer createProducerFor(
            final OpenAPI openapi, final Operation operation, final String method, final String uriTemplate)
            throws Exception {

        CamelContext camelContext = getCamelContext();

        Map<String, Object> params = determineEndpointParameters(openapi, operation);
        boolean hasHost = params.containsKey("host");
        String basePath = determineBasePath(openapi);
        String componentEndpointUri = "rest:" + method + ":" + basePath + ":" + uriTemplate;
        if (hasHost) {
            componentEndpointUri += "?host=" + params.get("host");
        }

        Endpoint endpoint = camelContext.getEndpoint(componentEndpointUri);
        // let the rest endpoint configure itself
        endpoint.configureProperties(params);

        RequestValidator requestValidator = null;
        if (requestValidationEnabled) {
            requestValidator = configureRequestValidator(openapi, operation, method, uriTemplate);
        }

        // if there is a host then we should use this hardcoded host instead of any Header that may have an existing
        // Host header from some other HTTP input, and if so then lets remove it
        return new RestOpenApiProducer(endpoint.createProducer(), hasHost, requestValidator);
    }

    String determineBasePath(final OpenAPI openapi) {
        if (isNotEmpty(basePath)) {
            return basePath;
        }

        final String componentBasePath = getComponent().getBasePath();
        if (isNotEmpty(componentBasePath)) {
            return componentBasePath;
        }

        final CamelContext camelContext = getCamelContext();
        final RestConfiguration restConfiguration =
                CamelContextHelper.getRestConfiguration(camelContext, null, determineComponentName());
        final String restConfigurationBasePath = restConfiguration.getContextPath();
        if (isNotEmpty(restConfigurationBasePath)) {
            return restConfigurationBasePath;
        }

        final String specificationBasePath = RestOpenApiHelper.getBasePathFromOpenApi(openapi);
        if (isNotEmpty(specificationBasePath)) {
            return specificationBasePath;
        }

        return RestOpenApiComponent.DEFAULT_BASE_PATH;
    }

    String determineComponentName() {
        return Optional.ofNullable(componentName).orElse(getComponent().getComponentName());
    }

    Map<String, Object> determineEndpointParameters(final OpenAPI openapi, final Operation operation) {
        final Map<String, Object> parameters = new HashMap<>();

        final String componentName = determineComponentName();
        if (componentName != null) {
            parameters.put("producerComponentName", componentName);
        }

        final String host = determineHost(openapi, operation);
        if (host != null) {
            parameters.put("host", host);
        }

        final RestOpenApiComponent component = getComponent();

        // what we consume is what the API defined by OpenApi specification
        // produces
        List<String> specificationLevelConsumers = new ArrayList<>();
        Set<String> operationLevelConsumers = new java.util.HashSet<>();
        if (operation.getResponses() != null) {
            for (ApiResponse response : operation.getResponses().values()) {
                if (response.getContent() != null) {
                    operationLevelConsumers.addAll(response.getContent().keySet());
                }
            }
        }

        final String determinedConsumes = determineOption(
                specificationLevelConsumers, operationLevelConsumers, component.getConsumes(), consumes);

        if (isNotEmpty(determinedConsumes)) {
            parameters.put("consumes", determinedConsumes);
        }

        // what we produce is what the API defined by OpenApi specification consumes
        List<String> specificationLevelProducers = new ArrayList<>();
        Set<String> operationLevelProducers = new java.util.HashSet<>();
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operationLevelProducers.addAll(
                    operation.getRequestBody().getContent().keySet());
        }

        final String determinedProducers = determineOption(
                specificationLevelProducers, operationLevelProducers, component.getProduces(), produces);

        if (isNotEmpty(determinedProducers)) {
            parameters.put("produces", determinedProducers);
        }

        final String queryParameters = determineQueryParameters(openapi, operation)
                .map(this::queryParameter)
                .collect(Collectors.joining("&"));
        if (isNotEmpty(queryParameters)) {
            parameters.put("queryParameters", queryParameters);
        }

        // pass properties that might be applied if the delegate component is
        // created, i.e. if it's not
        // present in the Camel Context already
        final Map<String, Object> componentParameters = new HashMap<>();

        if (component.isUseGlobalSslContextParameters()) {
            // by default it's false
            componentParameters.put("useGlobalSslContextParameters", component.isUseGlobalSslContextParameters());
        }
        if (component.getSslContextParameters() != null) {
            componentParameters.put("sslContextParameters", component.getSslContextParameters());
        }

        final Map<Object, Object> nestedParameters = new HashMap<>();
        if (!componentParameters.isEmpty()) {
            nestedParameters.put("component", componentParameters);
        }

        // Add rest endpoint parameters
        if (this.parameters != null) {
            if (operation.getParameters() != null) {
                for (Map.Entry<String, Object> entry : this.parameters.entrySet()) {
                    for (Parameter param : operation.getParameters()) {
                        // skip parameters that are part of the operation as path as otherwise
                        // it will be duplicated as query parameter as well
                        boolean clash =
                                "path".equals(param.getIn()) && entry.getKey().equals(param.getName());
                        if (!clash) {
                            nestedParameters.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } else {
                nestedParameters.putAll(this.parameters);
            }
        }

        if (!nestedParameters.isEmpty()) {
            // we're trying to set RestEndpoint.parameters['component']
            parameters.put("parameters", nestedParameters);
        }

        return parameters;
    }

    String determineHost(final OpenAPI openApi, Operation operation) {
        if (isNotEmpty(host)) {
            return host;
        }

        final String componentHost = getComponent().getHost();
        if (isNotEmpty(componentHost)) {
            return componentHost;
        }

        URI absoluteURI = null;
        URI relativeURI = null;
        Set<URI> operationURIs = getURIs(operation.getServers());
        // Check if at least one of them is absolute:
        Optional<URI> opURI = operationURIs.stream().filter(URI::isAbsolute).findFirst();
        if (opURI.isEmpty()) {
            // look for absolute at api level + possible relative URI for the operation
            Set<URI> apiURIs = getURIs(openApi.getServers());
            for (URI uri : apiURIs) {
                if (uri.isAbsolute()) {
                    absoluteURI = uri;
                } else {
                    relativeURI = uri;
                }
            }
            for (URI uri : operationURIs) {
                if (absoluteURI != null) {
                    absoluteURI = absoluteURI.resolve(uri);
                } else if (relativeURI != null && !relativeURI.equals(uri)) {
                    // concatenate the relativeURIs
                    relativeURI = relativeURI.resolve(uri);
                } else {
                    relativeURI = uri;
                }
            }
        } else {
            absoluteURI = opURI.get();
        }
        if (absoluteURI != null) {
            String scheme = absoluteURI.getScheme();
            String host = absoluteURI.getHost();
            int port = absoluteURI.getPort();
            if (isNotEmpty(scheme) && isNotEmpty(host)) {
                return scheme + "://" + host + (port > 0 ? ":" + port : "");
            }
        }

        final CamelContext camelContext = getCamelContext();
        final RestConfiguration globalRestConfiguration =
                CamelContextHelper.getRestConfiguration(camelContext, null, determineComponentName());
        final String globalConfigurationHost = hostFrom(globalRestConfiguration);

        if (globalConfigurationHost != null) {
            return globalConfigurationHost;
        }

        try {
            final URI uri = new URI(specificationUri);
            final String specificationScheme = uri.getScheme();
            // Perform a case-insensitive "startsWith" check that works for different locales
            String prefix = "http";
            if (uri.isAbsolute() && specificationScheme.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null)
                        .toString();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create a new URI from: " + specificationUri, e);
        }

        throw new IllegalStateException("Unable to determine destination host for requests. The OpenApi specification"
                + " does not specify `scheme` and `host` parameters, the specification URI is not absolute with `http` or"
                + " `https` scheme, and no RestConfigurations configured with `scheme`, `host` and `port` were found for `"
                + (determineComponentName() != null ? determineComponentName() : "default" + "` component")
                + " and there is no global RestConfiguration with those properties");
    }

    private Set<URI> getURIs(List<Server> servers) {
        Set<URI> uris = new java.util.HashSet<>();
        if (servers != null) {
            for (Server server : servers) {
                try {
                    uris.add(new URI(RestOpenApiHelper.parseVariables(server.getUrl(), server)));
                } catch (URISyntaxException e) {
                    // ignore
                }
            }
        }
        return uris;
    }

    String literalPathParameterValue(final Parameter parameter) {
        final String name = parameter.getName();
        final String valueStr = String.valueOf(parameters.get(name));
        return UnsafeUriCharactersEncoder.encode(valueStr);
    }

    String literalQueryParameterValue(final Parameter parameter) {
        final String name = parameter.getName();
        final String valueStr = String.valueOf(parameters.get(name));
        final String encoded = UnsafeUriCharactersEncoder.encode(valueStr);
        return name + "=" + encoded;
    }

    String queryParameter(final Parameter parameter) {
        final String name = parameter.getName();
        if (ObjectHelper.isEmpty(name)) {
            return "";
        }

        if (parameters.containsKey(name)) {
            return literalQueryParameterValue(parameter);
        }
        return queryParameterExpression(parameter);
    }

    String resolveUri(final String uriTemplate, final Map<String, Parameter> pathParameters) {
        if (pathParameters.isEmpty()) {
            return uriTemplate;
        }

        int start = uriTemplate.indexOf('{');
        if (start == -1) {
            return uriTemplate;
        }

        int pos = 0;
        final StringBuilder resolved = new StringBuilder(uriTemplate.length() * 2);
        while (start != -1) {
            resolved.append(uriTemplate, pos, start);

            final int end = uriTemplate.indexOf('}', start);
            final String name = uriTemplate.substring(start + 1, end);

            if (parameters.containsKey(name)) {
                final Parameter parameter = pathParameters.get(name);
                final Object value = literalPathParameterValue(parameter);
                resolved.append(value);
            } else {
                resolved.append('{').append(name).append('}');
            }

            pos = end + 1;
            start = uriTemplate.indexOf('{', pos);
        }

        if (pos < uriTemplate.length()) {
            resolved.append(uriTemplate, pos, uriTemplate.length());
        }

        return resolved.toString();
    }

    private RequestValidator configureRequestValidator(
            OpenAPI openAPI, Operation operation, String method, String uriTemplate) {
        DefaultRequestValidator answer = new DefaultRequestValidator();
        answer.setOperation(new RestOpenApiOperation(operation, method, uriTemplate));
        return answer;
    }

    static String determineOption(
            final List<String> specificationLevel,
            final Set<String> operationLevel,
            final String componentLevel,
            final String endpointLevel) {
        if (isNotEmpty(endpointLevel)) {
            return endpointLevel;
        }

        if (isNotEmpty(componentLevel)) {
            return componentLevel;
        }

        if (operationLevel != null && !operationLevel.isEmpty()) {
            return String.join(", ", operationLevel);
        }

        if (specificationLevel != null && !specificationLevel.isEmpty()) {
            return String.join(", ", specificationLevel);
        }

        return null;
    }

    static Stream<Parameter> determineQueryParameters(final OpenAPI openApi, final Operation operation) {
        final List<SecurityRequirement> securityRequirements = operation.getSecurity();
        final List<Parameter> securityQueryParameters = new ArrayList<>();
        if (securityRequirements != null) {
            final Map<String, SecurityScheme> securityDefinitions =
                    openApi.getComponents().getSecuritySchemes();

            for (final Map<String, List<String>> securityRequirement : securityRequirements) {
                for (final String securityRequirementName : securityRequirement.keySet()) {
                    final SecurityScheme securitySchemeDefinition = securityDefinitions.get(securityRequirementName);
                    if (In.QUERY.equals(securitySchemeDefinition.getIn())) {
                        securityQueryParameters.add(new Parameter()
                                .name(securitySchemeDefinition.getName())
                                .required(true)
                                .description(securitySchemeDefinition.getDescription()));
                        // Not needed to set schema or style?
                    }
                }
            }
        }

        if (operation.getParameters() != null) {
            return Stream.concat(
                    securityQueryParameters.stream(),
                    operation.getParameters().stream().filter(p -> "query".equals(p.getIn())));
        } else {
            return securityQueryParameters.stream();
        }
    }

    static String hostFrom(final RestConfiguration restConfiguration) {
        if (restConfiguration == null) {
            return null;
        }

        final String scheme = restConfiguration.getScheme();
        final String host = restConfiguration.getHost();
        final int port = restConfiguration.getPort();

        if (scheme == null || host == null) {
            return null;
        }

        final StringBuilder answer = new StringBuilder(scheme).append("://").append(host);
        if (port > 0
                && !("http".equalsIgnoreCase(scheme) && port == 80)
                && !("https".equalsIgnoreCase(scheme) && port == 443)) {
            answer.append(':').append(port);
        }

        return answer.toString();
    }

    /**
     * Loads the OpenApi definition model from the given path. This delegates directly to the OpenAPI parser. If the
     * specification can't be read there is no OpenAPI object in the result.
     *
     * @param  uri          the specification uri
     * @param  camelContext context to use
     * @return              the specification
     */
    static OpenAPI loadSpecificationFrom(final CamelContext camelContext, final String uri) {
        final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();
        final ParseOptions options = new ParseOptions();
        options.setResolveFully(true);

        InputStream is = null;
        try {
            String location = null;
            String content = null;
            Resource resource = ResourceHelper.resolveMandatoryResource(camelContext, uri);
            if (resource.getScheme().startsWith("http")) {
                location = resource.getURI().toString();
            } else {
                is = resource.getInputStream();
                if (is != null) {
                    content = IOHelper.loadText(is);
                }
            }
            SwaggerParseResult openApi = null;
            if (location != null) {
                openApi = openApiParser.readLocation(location, null, options);
            } else if (content != null) {
                openApi = openApiParser.readContents(content, null, options);
            }
            if (openApi != null && openApi.getOpenAPI() != null) {
                return openApi.getOpenAPI();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("The given OpenApi specification cannot be loaded from: " + uri, e);
        } finally {
            IOHelper.close(is);
        }

        // In theory there should be a message in the parse result, but it has disappeared...
        throw new IllegalArgumentException("The given OpenApi specification cannot be loaded from: " + uri);
    }

    static String pickBestScheme(final String specificationScheme, final List<String> schemes) {
        if (schemes != null && !schemes.isEmpty()) {
            if (schemes.contains("https")) {
                return "https";
            }
            if (schemes.contains("http")) {
                return "http";
            }
        }

        return specificationScheme;
        // there is no support for WebSocket (Scheme.WS, Scheme.WSS)
    }

    static String queryParameterExpression(final Parameter parameter) {
        final String name = parameter.getName();

        final StringBuilder expression = new StringBuilder(name).append("={").append(name);
        if (parameter.getRequired() == null || !parameter.getRequired()) {
            expression.append('?');
        }
        expression.append('}');

        return expression.toString();
    }

    private static class RestOpenApiProcessorAdvice implements CamelInternalProcessorAdvice<Object>, Ordered {

        private final RestOpenApiProcessor openApiProcessor;

        public RestOpenApiProcessorAdvice(RestOpenApiProcessor openApiProcessor) {
            this.openApiProcessor = openApiProcessor;
        }

        @Override
        public boolean hasState() {
            return false;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            try {
                openApiProcessor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            // noop
        }

        @Override
        public int getOrder() {
            // should be lowest so all existing advices are triggered first
            return Ordered.LOWEST;
        }
    }
}
