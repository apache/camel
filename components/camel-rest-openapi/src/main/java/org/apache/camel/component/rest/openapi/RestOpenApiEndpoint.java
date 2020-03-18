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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.core.models.Document;
import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.models.OasPaths;
import io.apicurio.datamodels.openapi.models.OasResponse;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityDefinitions;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Parameter;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import io.apicurio.datamodels.openapi.v3.models.Oas30SecurityScheme;
import io.apicurio.datamodels.openapi.v3.models.Oas30Server;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import static java.util.Optional.ofNullable;
import static org.apache.camel.component.rest.openapi.RestOpenApiHelper.isHostParam;
import static org.apache.camel.component.rest.openapi.RestOpenApiHelper.isMediaRange;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.after;
import static org.apache.camel.util.StringHelper.before;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * An awesome REST endpoint backed by OpenApi specifications.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "rest-openapi", title = "REST OpenApi",
    syntax = "rest-openapi:specificationUri#operationId", label = "rest,openapi,http", producerOnly = true)
public final class RestOpenApiEndpoint extends DefaultEndpoint {

    /**
     * Remaining parameters specified in the Endpoint URI.
     */
    Map<String, Object> parameters = Collections.emptyMap();

    /** The name of the Camel component, be it `rest-openapi` or `petstore` */
    private String assignedComponentName;

    @UriParam(
        description = "API basePath, for example \"`/v2`\". Default is unset, if set overrides the value present in"
            + " OpenApi specification and in the component configuration.",
        defaultValue = "", label = "producer")
    private String basePath;

    @UriParam(description = "Name of the Camel component that will perform the requests. The component must be present"
        + " in Camel registry and it must implement RestProducerFactory service provider interface. If not set"
        + " CLASSPATH is searched for single component that implements RestProducerFactory SPI. Overrides"
        + " component configuration.", label = "producer")
    private String componentName;

    @UriParam(
        description = "What payload type this component capable of consuming. Could be one type, like `application/json`"
            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
            + " to the value of `Accept` HTTP header. If set overrides any value found in the OpenApi specification and."
            + " in the component configuration",
        label = "producer")
    private String consumes;

    @UriParam(description = "Scheme hostname and port to direct the HTTP requests to in the form of"
        + " `http[s]://hostname[:port]`. Can be configured at the endpoint, component or in the corresponding"
        + " REST configuration in the Camel Context. If you give this component a name (e.g. `petstore`) that"
        + " REST configuration is consulted first, `rest-openapi` next, and global configuration last. If set"
        + " overrides any value found in the OpenApi specification, RestConfiguration. Overrides all other "
        + " configuration.", label = "producer")
    private String host;

    @UriPath(description = "ID of the operation from the OpenApi specification.", label = "producer")
    @Metadata(required = true)
    private String operationId;

    @UriParam(description = "What payload type this component is producing. For example `application/json`"
        + " according to the RFC7231. This equates to the value of `Content-Type` HTTP header. If set overrides"
        + " any value present in the OpenApi specification. Overrides all other configuration.", label = "producer")
    private String produces;

    @UriPath(description = "Path to the OpenApi specification file. The scheme, host base path are taken from this"
        + " specification, but these can be overridden with properties on the component or endpoint level. If not"
        + " given the component tries to load `openapi.json` resource from the classpath. Note that the `host` defined on the"
        + " component and endpoint of this Component should contain the scheme, hostname and optionally the"
        + " port in the URI syntax (i.e. `http://api.example.com:8080`). Overrides component configuration."
        + " The OpenApi specification can be loaded from different sources by prefixing with file: classpath: http: https:."
        + " Support for https is limited to using the JDK installed UrlHandler, and as such it can be cumbersome to setup"
        + " TLS/SSL certificates for https (such as setting a number of javax.net.ssl JVM system properties)."
        + " How to do that consult the JDK documentation for UrlHandler.",
        defaultValue = RestOpenApiComponent.DEFAULT_SPECIFICATION_URI_STR,
        defaultValueNote = "By default loads `openapi.json` file", label = "producer")
    private URI specificationUri = RestOpenApiComponent.DEFAULT_SPECIFICATION_URI;

    public RestOpenApiEndpoint() {
        // help tooling instantiate endpoint
    }

    public RestOpenApiEndpoint(final String uri, final String remaining, final RestOpenApiComponent component,
        final Map<String, Object> parameters) {
        super(notEmpty(uri, "uri"), notNull(component, "component"));
        this.parameters = parameters;

        assignedComponentName = before(uri, ":");

        final URI componentSpecificationUri = component.getSpecificationUri();

        specificationUri = before(remaining, "#", StringHelper::trimToNull).map(URI::create)
            .orElse(ofNullable(componentSpecificationUri).orElse(RestOpenApiComponent.DEFAULT_SPECIFICATION_URI));

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
        final Document openapiDoc = loadSpecificationFrom(camelContext, specificationUri);

        final OasPaths paths = ((OasDocument)openapiDoc).paths;

        for (final OasPathItem path : paths.getItems()) {
            final Optional<Entry<HttpMethod, OasOperation>> maybeOperationEntry = getOperationMap(path).entrySet()
                .stream().filter(operationEntry -> operationId.equals(operationEntry.getValue().operationId))
                .findAny();

            if (maybeOperationEntry.isPresent()) {
                final Entry<HttpMethod, OasOperation> operationEntry = maybeOperationEntry.get();

                final OasOperation operation = operationEntry.getValue();
                Map<String, OasParameter> pathParameters = null;
                if (operation.getParameters() != null) {
                    pathParameters = operation.getParameters().stream()
                        .filter(p -> "path".equals(p.in))
                        .collect(Collectors.toMap(OasParameter::getName, Function.identity()));
                } else {
                    pathParameters = new HashMap<String, OasParameter>();
                }
                final String uriTemplate = resolveUri(path.getPath(), pathParameters);

                final HttpMethod httpMethod = operationEntry.getKey();
                final String method = httpMethod.name();

                return createProducerFor(openapiDoc, operation, method, uriTemplate);
            }
        }



        String supportedOperations = paths.getItems().stream().flatMap(p -> getOperationMap(p).values().stream())
            .map(p -> p.operationId).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("The specified operation with ID: `" + operationId
            + "` cannot be found in the OpenApi specification loaded from `" + specificationUri
            + "`. Operations defined in the specification are: " + supportedOperations);
    }


    private Map<HttpMethod, OasOperation> getOperationMap(OasPathItem path) {
        Map<HttpMethod, OasOperation> result = new LinkedHashMap<HttpMethod, OasOperation>();

        if (path.get != null) {
            result.put(HttpMethod.GET, path.get);
        }
        if (path.put != null) {
            result.put(HttpMethod.PUT, path.put);
        }
        if (path.post != null) {
            result.put(HttpMethod.POST, path.post);
        }
        if (path.delete != null) {
            result.put(HttpMethod.DELETE, path.delete);
        }
        if (path.patch != null) {
            result.put(HttpMethod.PATCH, path.patch);
        }
        if (path.head != null) {
            result.put(HttpMethod.HEAD, path.head);
        }
        if (path.options != null) {
            result.put(HttpMethod.OPTIONS, path.options);
        }

        return result;
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

    RestOpenApiComponent component() {
        return (RestOpenApiComponent) getComponent();
    }

    Producer createProducerFor(final Document openapi, final OasOperation operation, final String method,
        final String uriTemplate) throws Exception {
        final String basePath = determineBasePath(openapi);
        final String componentEndpointUri = "rest:" + method + ":" + basePath + ":" + uriTemplate;

        final CamelContext camelContext = getCamelContext();

        final Endpoint endpoint = camelContext.getEndpoint(componentEndpointUri);

        Map<String, Object> params = determineEndpointParameters(openapi, operation);
        boolean hasHost = params.containsKey("host");
        // let the rest endpoint configure itself
        endpoint.configureProperties(params);

        // if there is a host then we should use this hardcoded host instead of any Header that may have an existing
        // Host header from some other HTTP input, and if so then lets remove it
        return new RestOpenApiProducer(endpoint.createAsyncProducer(), hasHost);
    }

    String determineBasePath(final Document openapi) {
        if (isNotEmpty(basePath)) {
            return basePath;
        }

        final String componentBasePath = component().getBasePath();
        if (isNotEmpty(componentBasePath)) {
            return componentBasePath;
        }

        final String specificationBasePath = getBasePathFromOasDocument((OasDocument)openapi);

        if (isNotEmpty(specificationBasePath)) {
            return specificationBasePath;
        }

        final CamelContext camelContext = getCamelContext();
        final RestConfiguration restConfiguration = CamelContextHelper.getRestConfiguration(camelContext, assignedComponentName);
        final String restConfigurationBasePath = restConfiguration.getContextPath();

        if (isNotEmpty(restConfigurationBasePath)) {
            return restConfigurationBasePath;
        }

        return RestOpenApiComponent.DEFAULT_BASE_PATH;
    }

    public static String getBasePathFromOasDocument(final OasDocument openapi) {
        String basePath = null;
        if (openapi instanceof Oas20Document) {
            basePath = ((Oas20Document)openapi).basePath;
        } else if (openapi instanceof Oas30Document) {
            if (((Oas30Document)openapi).getServers() != null
                && ((Oas30Document)openapi).getServers().get(0) != null) {
                try {
                    Oas30Server server = (Oas30Server)((Oas30Document)openapi).getServers().get(0);
                    if (server.variables != null && server.variables.get("basePath") != null) {
                        basePath = server.variables.get("basePath").default_;
                    }
                    if (basePath == null) {
                        // parse server url as fallback
                        URL serverUrl = new URL(parseVariables(((Oas30Document)openapi).getServers().get(0).url, server));
                        basePath = serverUrl.getPath();
                        if (basePath.indexOf("//") == 0) {
                            // strip off the first "/" if double "/" exists
                            basePath = basePath.substring(1);
                        }
                        if ("/".equals(basePath)) {
                            basePath = "";
                        }
                    }

                } catch (MalformedURLException e) {
                    //not a valid whole url, just the basePath
                    basePath = ((Oas30Document)openapi).getServers().get(0).url;
                }
            }

        }
        return basePath;

    }

    public static String parseVariables(String url, Oas30Server server) {
        Pattern p = Pattern.compile("\\{(.*?)\\}");
        Matcher m = p.matcher(url);
        while (m.find()) {

            String var = m.group(1);
            if (server != null && server.variables != null && server.variables.get(var) != null) {
                String varValue = server.variables.get(var).default_;
                url = url.replace("{" + var + "}", varValue);
            }
        }
        return url;
    }

    String determineComponentName() {
        return Optional.ofNullable(componentName).orElse(component().getComponentName());
    }

    Map<String, Object> determineEndpointParameters(final Document openapi, final OasOperation operation) {
        final Map<String, Object> parameters = new HashMap<>();

        final String componentName = determineComponentName();
        if (componentName != null) {
            parameters.put("producerComponentName", componentName);
        }

        final String host = determineHost(openapi);
        if (host != null) {
            parameters.put("host", host);
        }

        final RestOpenApiComponent component = component();

        // what we consume is what the API defined by OpenApi specification
        // produces
        List<String> specificationLevelConsumers = new ArrayList<String>();
        if (openapi instanceof Oas20Document) {
            specificationLevelConsumers = ((Oas20Document)openapi).produces;
        }
        List<String> operationLevelConsumers = new ArrayList<String>();
        if (operation instanceof Oas20Operation) {
            operationLevelConsumers = ((Oas20Operation)operation).produces;
        } else if (operation instanceof Oas30Operation) {
            Oas30Operation oas30Operation = (Oas30Operation)operation;
            if (oas30Operation.responses != null) {
                for (OasResponse response : oas30Operation.responses.getResponses()) {
                    Oas30Response oas30Response = (Oas30Response)response;
                    for (String ct : oas30Response.content.keySet()) {
                        operationLevelConsumers.add(ct);
                    }
                }
            }
        }
        final String determinedConsumes = determineOption(specificationLevelConsumers, operationLevelConsumers,
            component.getConsumes(), consumes);

        if (isNotEmpty(determinedConsumes)) {
            parameters.put("consumes", determinedConsumes);
        }

        // what we produce is what the API defined by OpenApi specification
        // consumes

        List<String> specificationLevelProducers = new ArrayList<String>();
        if (openapi instanceof Oas20Document) {
            specificationLevelProducers = ((Oas20Document)openapi).consumes;
        }
        List<String> operationLevelProducers = new ArrayList<String>();
        if (operation instanceof Oas20Operation) {
            operationLevelProducers = ((Oas20Operation)operation).consumes;
        } else if (operation instanceof Oas30Operation) {
            Oas30Operation oas30Operation = (Oas30Operation)operation;
            if (oas30Operation.requestBody != null
                && oas30Operation.requestBody.content != null) {
                for (String ct : oas30Operation.requestBody.content.keySet()) {
                    operationLevelProducers.add(ct);
                }
            }

        }

        final String determinedProducers = determineOption(specificationLevelProducers, operationLevelProducers,
            component.getProduces(), produces);

        if (isNotEmpty(determinedProducers)) {
            parameters.put("produces", determinedProducers);
        }

        final String queryParameters = determineQueryParameters(openapi, operation).map(this::queryParameter)
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

        if (!componentParameters.isEmpty()) {
            final Map<Object, Object> nestedParameters = new HashMap<>();
            nestedParameters.put("component", componentParameters);

            // we're trying to set RestEndpoint.parameters['component']
            parameters.put("parameters", nestedParameters);
        }

        return parameters;
    }

    String determineHost(final Document openapi) {
        if (isNotEmpty(host)) {
            return host;
        }

        final String componentHost = component().getHost();
        if (isNotEmpty(componentHost)) {
            return componentHost;
        }



        if (openapi instanceof Oas20Document) {
            final String openapiScheme = pickBestScheme(specificationUri.getScheme(), ((Oas20Document)openapi).schemes);
            final String openapiHost = ((Oas20Document)openapi).host;

            if (isNotEmpty(openapiScheme) && isNotEmpty(openapiHost)) {
                return openapiScheme + "://" + openapiHost;
            }
        } else if (openapi instanceof Oas30Document) {
            //In OpenApi 3.0, scheme/host are in servers url section
            //But there could be many servers url(like one for production and one for test)
            //Use first one here
            Oas30Document oas30Document = (Oas30Document)openapi;
            if (oas30Document.getServers() != null
                && oas30Document.getServers().get(0) != null) {
                try {

                    URL serverUrl = new URL(parseVariables(oas30Document.getServers().get(0).url, (Oas30Server)oas30Document.getServers().get(0)));
                    final String openapiScheme = serverUrl.getProtocol();
                    final String openapiHost = serverUrl.getHost();
                    if (isNotEmpty(openapiScheme) && isNotEmpty(openapiHost)) {
                        return openapiScheme + "://" + openapiHost;
                    }
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        final CamelContext camelContext = getCamelContext();
        final RestConfiguration globalRestConfiguration = CamelContextHelper.getRestConfiguration(camelContext, assignedComponentName);
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

        final boolean areTheSame = "rest-openapi".equals(assignedComponentName);

        throw new IllegalStateException("Unable to determine destination host for requests. The OpenApi specification"
            + " does not specify `scheme` and `host` parameters, the specification URI is not absolute with `http` or"
            + " `https` scheme, and no RestConfigurations configured with `scheme`, `host` and `port` were found for `"
            + (areTheSame ? "rest-openapi` component" : assignedComponentName + "` or `rest-openapi` components")
            + " and there is no global RestConfiguration with those properties");
    }

    String literalPathParameterValue(final OasParameter parameter) {
        final String name = parameter.getName();

        final String valueStr = String.valueOf(parameters.get(name));
        final String encoded = UnsafeUriCharactersEncoder.encode(valueStr);

        return encoded;
    }

    String literalQueryParameterValue(final OasParameter parameter) {
        final String name = parameter.getName();

        final String valueStr = String.valueOf(parameters.get(name));
        final String encoded = UnsafeUriCharactersEncoder.encode(valueStr);

        return name + "=" + encoded;
    }

    String queryParameter(final OasParameter parameter) {
        final String name = parameter.getName();
        if (ObjectHelper.isEmpty(name)) {
            return "";
        }

        if (parameters.containsKey(name)) {
            return literalQueryParameterValue(parameter);
        }

        return queryParameterExpression(parameter);
    }

    String resolveUri(final String uriTemplate, final Map<String, OasParameter> pathParameters) {
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
                final OasParameter parameter = pathParameters.get(name);
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

    static Stream<OasParameter> determineQueryParameters(final Document openapi, final OasOperation operation) {
        final List<SecurityRequirement> securityRequirements = operation.security;
        final List<OasParameter> apiKeyQueryParameters = new ArrayList<>();
        if (securityRequirements != null) {
            if (openapi instanceof Oas20Document) {
                Oas20Document oas20Document = (Oas20Document)openapi;
                Oas20SecurityDefinitions securityDefinitions = oas20Document.securityDefinitions;

                for (final SecurityRequirement securityRequirement : securityRequirements) {
                    for (final String securityRequirementName : securityRequirement.getSecurityRequirementNames()) {
                        final Oas20SecurityScheme securitySchemeDefinition = securityDefinitions
                            .getSecurityScheme(securityRequirementName);
                        if (securitySchemeDefinition.in != null
                            && securitySchemeDefinition.in.equals("query")) {
                            Oas20Parameter securityParameter = new Oas20Parameter(securitySchemeDefinition.name);
                            securityParameter.required = true;
                            securityParameter.type = "string";
                            securityParameter.description = securitySchemeDefinition.description;
                            apiKeyQueryParameters.add(securityParameter);
                        }

                    }
                }
            } else if (openapi instanceof Oas30Document) {
                Oas30Document oas30Document = (Oas30Document)openapi;
                for (final SecurityRequirement securityRequirement : securityRequirements) {
                    for (final String securityRequirementName : securityRequirement.getSecurityRequirementNames()) {
                        final Oas30SecurityScheme securitySchemeDefinition = oas30Document.components
                            .getSecurityScheme(securityRequirementName);
                        if (securitySchemeDefinition.in != null && securitySchemeDefinition.in.equals("query")) {
                            Oas30Parameter securityParameter = new Oas30Parameter(securitySchemeDefinition.name);
                            securityParameter.required = true;
                            securityParameter.description = securitySchemeDefinition.description;
                            apiKeyQueryParameters.add(securityParameter);
                        }

                    }
                }
            } else {
                throw new IllegalStateException("We only support OpenApi 2.0 or 3.0 document here");
            }

        }

        if (operation.getParameters() != null) {
            return Stream.concat(apiKeyQueryParameters.stream(),
                                 operation.getParameters().stream().filter(p -> "query".equals(p.in)));
        } else {
            return apiKeyQueryParameters.stream();
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
        if (port > 0 && !("http".equalsIgnoreCase(scheme) && port == 80)
            && !("https".equalsIgnoreCase(scheme) && port == 443)) {
            answer.append(':').append(port);
        }

        return answer.toString();
    }

    /**
     * Loads the OpenApi definition model from the given path. Tries to resolve
     * the resource using Camel's resource loading support, if it fails uses
     * OpenApi's resource loading support instead.
     *
     * @param uri URI of the specification
     * @param camelContext context to use
     * @return the specification
     * @throws IOException
     */
    static Document loadSpecificationFrom(final CamelContext camelContext, final URI uri) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();


        final String uriAsString = uri.toString();

        try (InputStream stream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uriAsString)) {
            final JsonNode node = mapper.readTree(stream);

            return Library.readDocument(node);
        } catch (final Exception e) {

            throw new IllegalArgumentException("The given OpenApi specification could not be loaded from `" + uri
                + "`. Tried loading using Camel's resource resolution and using OpenApi's own resource resolution."
                + " OpenApi tends to swallow exceptions while parsing, try specifying Java system property `debugParser`"
                + " (e.g. `-DdebugParser=true`), the exception that occurred when loading using Camel's resource"
                + " loader follows", e);
        }
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

        if (specificationScheme != null) {
            return specificationScheme;
        }

        // there is no support for WebSocket (Scheme.WS, Scheme.WSS)

        return null;
    }

    static String queryParameterExpression(final OasParameter parameter) {
        final String name = parameter.getName();

        final StringBuilder expression = new StringBuilder(name).append("={").append(name);
        if (parameter.required == null || !parameter.required) {
            expression.append('?');
        }
        expression.append('}');

        return expression.toString();
    }

    enum HttpMethod {
        POST,
        GET,
        PUT,
        PATCH,
        DELETE,
        HEAD,
        OPTIONS
    }

}
