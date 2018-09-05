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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.rest.swagger.RestSwaggerHelper.isHostParam;
import static org.apache.camel.component.rest.swagger.RestSwaggerHelper.isMediaRange;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.after;
import static org.apache.camel.util.StringHelper.before;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * An awesome REST endpoint backed by Swagger specifications.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "rest-swagger", title = "REST Swagger",
    syntax = "rest-swagger:specificationUri#operationId", label = "rest,swagger,http", producerOnly = true)
public final class RestSwaggerEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerEndpoint.class);

    /**
     * Remaining parameters specified in the Endpoint URI.
     */
    Map<String, Object> parameters = Collections.emptyMap();

    /** The name of the Camel component, be it `rest-swagger` or `petstore` */
    private String assignedComponentName;

    @UriParam(
        description = "API basePath, for example \"`/v2`\". Default is unset, if set overrides the value present in"
            + " Swagger specification and in the component configuration.",
        defaultValue = "", label = "producer")
    @Metadata(required = "false")
    private String basePath;

    @UriParam(description = "Name of the Camel component that will perform the requests. The compnent must be present"
        + " in Camel registry and it must implement RestProducerFactory service provider interface. If not set"
        + " CLASSPATH is searched for single component that implements RestProducerFactory SPI. Overrides"
        + " component configuration.", label = "producer")
    @Metadata(required = "false")
    private String componentName;

    @UriParam(
        description = "What payload type this component capable of consuming. Could be one type, like `application/json`"
            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
            + " to the value of `Accept` HTTP header. If set overrides any value found in the Swagger specification and."
            + " in the component configuration",
        label = "producer")
    private String consumes;

    @UriParam(description = "Scheme hostname and port to direct the HTTP requests to in the form of"
        + " `http[s]://hostname[:port]`. Can be configured at the endpoint, component or in the correspoding"
        + " REST configuration in the Camel Context. If you give this component a name (e.g. `petstore`) that"
        + " REST configuration is consulted first, `rest-swagger` next, and global configuration last. If set"
        + " overrides any value found in the Swagger specification, RestConfiguration. Overrides all other "
        + " configuration.", label = "producer")
    private String host;

    @UriPath(description = "ID of the operation from the Swagger specification.", label = "producer")
    @Metadata(required = "true")
    private String operationId;

    @UriParam(description = "What payload type this component is producing. For example `application/json`"
        + " according to the RFC7231. This equates to the value of `Content-Type` HTTP header. If set overrides"
        + " any value present in the Swagger specification. Overrides all other configuration.", label = "producer")
    private String produces;

    @UriPath(
        description = "Path to the Swagger specification file. The scheme, host base path are taken from this"
            + " specification, but these can be overriden with properties on the component or endpoint level. If not"
            + " given the component tries to load `swagger.json` resource. Note that the `host` defined on the"
            + " component and endpoint of this Component should contain the scheme, hostname and optionally the"
            + " port in the URI syntax (i.e. `https://api.example.com:8080`). Overrides component configuration.",
        defaultValue = RestSwaggerComponent.DEFAULT_SPECIFICATION_URI_STR,
        defaultValueNote = "By default loads `swagger.json` file", label = "producer")
    private URI specificationUri = RestSwaggerComponent.DEFAULT_SPECIFICATION_URI;

    public RestSwaggerEndpoint() {
        // help tooling instantiate endpoint
    }

    public RestSwaggerEndpoint(final String uri, final String remaining, final RestSwaggerComponent component,
        final Map<String, Object> parameters) {
        super(notEmpty(uri, "uri"), notNull(component, "component"));
        this.parameters = parameters;

        assignedComponentName = before(uri, ":");

        final URI componentSpecificationUri = component.getSpecificationUri();

        specificationUri = before(remaining, "#", StringHelper::trimToNull).map(URI::create)
            .orElse(ofNullable(componentSpecificationUri).orElse(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI));

        operationId = ofNullable(after(remaining, "#")).orElse(remaining);

        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public Producer createProducer() throws Exception {
        final CamelContext camelContext = getCamelContext();
        final Swagger swagger = loadSpecificationFrom(camelContext, specificationUri);

        final Map<String, Path> paths = swagger.getPaths();

        for (final Entry<String, Path> pathEntry : paths.entrySet()) {
            final Path path = pathEntry.getValue();

            final Optional<Entry<HttpMethod, Operation>> maybeOperationEntry = path.getOperationMap().entrySet()
                .stream().filter(operationEntry -> operationId.equals(operationEntry.getValue().getOperationId()))
                .findAny();

            if (maybeOperationEntry.isPresent()) {
                final Entry<HttpMethod, Operation> operationEntry = maybeOperationEntry.get();

                final Operation operation = operationEntry.getValue();
                final Map<String, Parameter> pathParameters = operation.getParameters().stream()
                    .filter(p -> "path".equals(p.getIn()))
                    .collect(Collectors.toMap(Parameter::getName, Function.identity()));
                final String uriTemplate = resolveUri(pathEntry.getKey(), pathParameters);

                final HttpMethod httpMethod = operationEntry.getKey();
                final String method = httpMethod.name();

                return createProducerFor(swagger, operation, method, uriTemplate);
            }
        }

        final String supportedOperations = paths.values().stream().flatMap(p -> p.getOperations().stream())
            .map(Operation::getOperationId).collect(Collectors.joining(", "));

        throw new IllegalArgumentException("The specified operation with ID: `" + operationId
            + "` cannot be found in the Swagger specification loaded from `" + specificationUri
            + "`. Operations defined in the specification are: " + supportedOperations);
    }

    public String getBasePath() {
        return basePath;
    }

    public String getComponentName() {
        return componentName;
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

    public URI getSpecificationUri() {
        return specificationUri;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setBasePath(final String basePath) {
        this.basePath = notEmpty(basePath, "basePath");
    }

    public void setComponentName(final String componentName) {
        this.componentName = notEmpty(componentName, "componentName");
    }

    public void setConsumes(final String consumes) {
        this.consumes = isMediaRange(consumes, "consumes");
    }

    public void setHost(final String host) {
        this.host = isHostParam(host);
    }

    public void setOperationId(final String operationId) {
        this.operationId = notEmpty(operationId, "operationId");
    }

    public void setProduces(final String produces) {
        this.produces = isMediaRange(produces, "produces");
    }

    public void setSpecificationUri(final URI specificationUri) {
        this.specificationUri = notNull(specificationUri, "specificationUri");
    }

    RestSwaggerComponent component() {
        return (RestSwaggerComponent) getComponent();
    }

    Producer createProducerFor(final Swagger swagger, final Operation operation, final String method,
        final String uriTemplate) throws Exception {
        final String basePath = determineBasePath(swagger);

        final StringBuilder componentEndpointUri = new StringBuilder(200).append("rest:").append(method).append(":")
            .append(basePath).append(":").append(uriTemplate);

        final CamelContext camelContext = getCamelContext();

        final Endpoint endpoint = camelContext.getEndpoint(componentEndpointUri.toString());

        setProperties(endpoint, determineEndpointParameters(swagger, operation));

        return endpoint.createProducer();
    }

    String determineBasePath(final Swagger swagger) {
        if (isNotEmpty(basePath)) {
            return basePath;
        }

        final String componentBasePath = component().getBasePath();
        if (isNotEmpty(componentBasePath)) {
            return componentBasePath;
        }

        final String specificationBasePath = swagger.getBasePath();
        if (isNotEmpty(specificationBasePath)) {
            return specificationBasePath;
        }

        final CamelContext camelContext = getCamelContext();
        final RestConfiguration specificConfiguration = camelContext.getRestConfiguration(assignedComponentName, false);
        if (specificConfiguration != null && isNotEmpty(specificConfiguration.getContextPath())) {
            return specificConfiguration.getContextPath();
        }

        final RestConfiguration restConfiguration = camelContext.getRestConfiguration("rest-swagger", true);
        final String restConfigurationBasePath = restConfiguration.getContextPath();
        if (isNotEmpty(restConfigurationBasePath)) {
            return restConfigurationBasePath;
        }

        return RestSwaggerComponent.DEFAULT_BASE_PATH;
    }

    String determineComponentName() {
        return Optional.ofNullable(componentName).orElse(component().getComponentName());
    }

    Map<String, Object> determineEndpointParameters(final Swagger swagger, final Operation operation) {
        final Map<String, Object> parameters = new HashMap<>();

        final String componentName = determineComponentName();
        if (componentName != null) {
            parameters.put("componentName", componentName);
        }

        final String host = determineHost(swagger);
        if (host != null) {
            parameters.put("host", host);
        }

        final RestSwaggerComponent component = component();

        // what we consume is what the API defined by Swagger specification
        // produces
        final String determinedConsumes = determineOption(swagger.getProduces(), operation.getProduces(),
            component.getConsumes(), consumes);

        if (isNotEmpty(determinedConsumes)) {
            parameters.put("consumes", determinedConsumes);
        }

        // what we produce is what the API defined by Swagger specification
        // consumes
        final String determinedProducers = determineOption(swagger.getConsumes(), operation.getConsumes(),
            component.getProduces(), produces);

        if (isNotEmpty(determinedProducers)) {
            parameters.put("produces", determinedProducers);
        }

        final String queryParameters = operation.getParameters().stream().filter(p -> "query".equals(p.getIn()))
            .map(this::queryParameter).collect(Collectors.joining("&"));
        if (isNotEmpty(queryParameters)) {
            parameters.put("queryParameters", queryParameters);
        }

        // pass properties that might be applied if the delegate component is created, i.e. if it's not
        // present in the Camel Context already
        final Map<String, Object> componentParameters = new HashMap<>();

        if (component.isUseGlobalSslContextParameters()) {
            // by default it's false
            componentParameters.put("useGlobalSslContextParameters", component.isUseGlobalSslContextParameters());
        }
        if (component.getSslContextParameters() != null) {
            componentParameters.put("sslContextParameters", component.getSslContextParameters());
        }

        if (!componentParameters.isEmpty()) {
            final Map<Object, Object> nestedParameters = new HashMap<>();
            nestedParameters.put("component", componentParameters);

            // we're trying to set RestEndpoint.parameters['component']
            parameters.put("parameters", nestedParameters);
        }

        return parameters;
    }

    String determineHost(final Swagger swagger) {
        if (isNotEmpty(host)) {
            return host;
        }

        final String componentHost = component().getHost();
        if (isNotEmpty(componentHost)) {
            return componentHost;
        }

        final String swaggerScheme = pickBestScheme(specificationUri.getScheme(), swagger.getSchemes());
        final String swaggerHost = swagger.getHost();

        if (isNotEmpty(swaggerScheme) && isNotEmpty(swaggerHost)) {
            return swaggerScheme + "://" + swaggerHost;
        }

        final CamelContext camelContext = getCamelContext();

        final RestConfiguration specificRestConfiguration = camelContext.getRestConfiguration(assignedComponentName,
            false);
        final String specificConfigurationHost = hostFrom(specificRestConfiguration);
        if (specificConfigurationHost != null) {
            return specificConfigurationHost;
        }

        final RestConfiguration componentRestConfiguration = camelContext.getRestConfiguration("rest-swagger", false);
        final String componentConfigurationHost = hostFrom(componentRestConfiguration);
        if (componentConfigurationHost != null) {
            return componentConfigurationHost;
        }

        final RestConfiguration globalRestConfiguration = camelContext.getRestConfiguration();
        final String globalConfigurationHost = hostFrom(globalRestConfiguration);
        if (globalConfigurationHost != null) {
            return globalConfigurationHost;
        }

        final String specificationScheme = specificationUri.getScheme();
        if (specificationUri.isAbsolute() && specificationScheme.toLowerCase().startsWith("http")) {
            try {
                return new URI(specificationUri.getScheme(), specificationUri.getUserInfo(), specificationUri.getHost(),
                    specificationUri.getPort(), null, null, null).toString();
            } catch (final URISyntaxException e) {
                throw new IllegalStateException("Unable to create a new URI from: " + specificationUri, e);
            }
        }

        final boolean areTheSame = "rest-swagger".equals(assignedComponentName);

        throw new IllegalStateException("Unable to determine destionation host for requests. The Swagger specification"
            + " does not specify `scheme` and `host` parameters, the specification URI is not absolute with `http` or"
            + " `https` scheme, and no RestConfigurations configured with `scheme`, `host` and `port` were found for `"
            + (areTheSame ? "rest-swagger` component" : assignedComponentName + "` or `rest-swagger` components")
            + " and there is no global RestConfiguration with those properties");
    }

    String literalPathParameterValue(final Parameter parameter) {
        final String name = parameter.getName();

        final String valueStr = String.valueOf(parameters.get(name));
        final String encoded = UnsafeUriCharactersEncoder.encode(valueStr);

        return encoded;
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
            resolved.append(uriTemplate.substring(pos, start));

            final int end = uriTemplate.indexOf('}', start);

            final String name = uriTemplate.substring(start + 1, end);

            if (parameters.containsKey(name)) {
                final Parameter parameter = pathParameters.get(name);
                final Object value = literalPathParameterValue(parameter);
                resolved.append(value);
            } else {
                resolved.append('{').append(name).append('}');
            }

            pos = end;
            start = uriTemplate.indexOf('{', pos);
        }

        return resolved.toString();
    }

    static String determineOption(final List<String> specificationLevel, final List<String> operationLevel,
        final String componentLevel, final String endpointLevel) {
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
        if (port > 0 && !("http".equalsIgnoreCase(scheme) && port == 80)
            && !("https".equalsIgnoreCase(scheme) && port == 443)) {
            answer.append(':').append(port);
        }

        return answer.toString();
    }

    /**
     * Loads the Swagger definition model from the given path. Tries to resolve
     * the resource using Camel's resource loading support, if it fails uses
     * Swagger's resource loading support instead.
     *
     * @param uri URI of the specification
     * @param camelContext context to use
     * @return the specification
     * @throws IOException
     */
    static Swagger loadSpecificationFrom(final CamelContext camelContext, final URI uri) throws IOException {
        final ObjectMapper mapper = Json.mapper();

        final SwaggerParser swaggerParser = new SwaggerParser();

        final String uriAsString = uri.toString();

        try (InputStream stream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uriAsString)) {
            final JsonNode node = mapper.readTree(stream);

            return swaggerParser.read(node);
        } catch (final IOException e) {
            // try Swaggers loader
            final Swagger swagger = swaggerParser.read(uriAsString);

            if (swagger != null) {
                return swagger;
            }

            throw new IllegalArgumentException("The given Swagger specification could not be loaded from `" + uri
                + "`. Tried loading using Camel's resource resolution and using Swagger's own resource resolution."
                + " Swagger tends to swallow exceptions while parsing, try specifying Java system property `debugParser`"
                + " (e.g. `-DdebugParser=true`), the exception that occured when loading using Camel's resource"
                + " loader follows", e);
        }
    }

    static String pickBestScheme(final String specificationScheme, final List<Scheme> schemes) {
        if (schemes != null && !schemes.isEmpty()) {
            if (schemes.contains(Scheme.HTTPS)) {
                return "https";
            }

            if (schemes.contains(Scheme.HTTP)) {
                return "http";
            }
        }

        if (specificationScheme != null) {
            return specificationScheme;
        }

        // there is no support for WebSocket (Scheme.WS, Scheme.WSS)

        return null;
    }

    static String queryParameterExpression(final Parameter parameter) {
        final String name = parameter.getName();

        final StringBuilder expression = new StringBuilder(name).append("={").append(name);
        if (!parameter.getRequired()) {
            expression.append('?');
        }
        expression.append('}');

        return expression.toString();
    }

}
