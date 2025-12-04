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
import static org.apache.camel.component.rest.openapi.RestOpenApiHelper.isMediaRange;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * An awesome REST component backed by OpenApi specifications. Creates endpoints that connect to REST APIs defined by
 * OpenApi specification. This component delegates to other {@link RestProducerFactory} components to act as REST
 * clients, but it configures them from OpenApi specification. Client needs to point to operation that it wants to
 * invoke via REST, provide any additional HTTP headers as headers in the Camel message, and any payload as the body of
 * the incoming message.
 * <p>
 * Example usage using Java DSL:
 * <p>
 *
 * <pre>
 * from(...).to("rest-openapi:https://petstore3.swagger.io/api/v3/openapi.json#getPetById")
 * </pre>
 * <p>
 * This relies on only one {@link RestProducerFactory} component being available to Camel, you can use specific, for
 * instance preconfigured component by using the {@code componentName} endpoint property. For example using Undertow
 * component in Java DSL:
 * <p>
 *
 * <pre>
 * Component undertow = new UndertowComponent();
 * undertow.setSslContextParameters(...);
 * //...
 * camelContext.addComponent("myUndertow", undertow);
 *
 * from(...).to("rest-openapi:https://petstore3.swagger.io/api/v3/openapi.json#getPetById?componentName=myUndertow")
 * </pre>
 * <p>
 * The most concise way of using this component would be to define it in the Camel context under a meaningful name, for
 * example:
 *
 * <pre>
 * Component petstore = new RestOpenApiComponent();
 * petstore.setSpecificationUri("https://petstore3.swagger.io/api/v3/openapi.json");
 * petstore.setComponentName("undertow");
 * //...
 * camelContext.addComponent("petstore", petstore);
 *
 * from(...).to("petstore:getPetById")
 * </pre>
 */
@Component("rest-openapi")
public final class RestOpenApiComponent extends DefaultComponent implements SSLContextParametersAware {

    public static final String DEFAULT_BASE_PATH = "/";

    static final String DEFAULT_SPECIFICATION_URI = "openapi.json";

    @Metadata(
            description = "Path to the OpenApi specification file. The scheme, host base path are taken from this"
                    + " specification, but these can be overridden with properties on the component or endpoint level. If not"
                    + " given the component tries to load `openapi.json` resource. Note that the `host` defined on the"
                    + " component and endpoint of this Component should contain the scheme, hostname and optionally the"
                    + " port in the URI syntax (i.e. `https://api.example.com:8080`). Can be overridden in endpoint"
                    + " configuration.",
            label = "common")
    private String specificationUri;

    @Metadata(
            description =
                    "API basePath, for example \"`/v2`\". Default is unset, if set overrides the value present in OpenApi specification.",
            label = "common")
    private String basePath = "";

    @Metadata(
            description = "Name of the Camel component that will perform the requests. The component must be present"
                    + " in Camel registry and it must implement RestProducerFactory service provider interface. If not set"
                    + " CLASSPATH is searched for single component that implements RestProducerFactory SPI. Can be overridden in"
                    + " endpoint configuration.",
            label = "producer,advanced")
    private String componentName;

    @Metadata(
            description = "Name of the Camel component that will service the requests. The component must be present"
                    + " in Camel registry and it must implement RestOpenApiConsumerFactory service provider interface. If not set"
                    + " CLASSPATH is searched for single component that implements RestOpenApiConsumerFactory SPI.  Can be overridden in"
                    + " endpoint configuration.",
            label = "consumer,advanced")
    private String consumerComponentName;

    @Metadata(
            description = "Scheme hostname and port to direct the HTTP requests to in the form of"
                    + " `http[s]://hostname[:port]`. Can be configured at the endpoint, component or in the corresponding"
                    + " REST configuration in the Camel Context. If you give this component a name (e.g. `petstore`) that"
                    + " REST configuration is consulted first, `rest-openapi` next, and global configuration last. If set"
                    + " overrides any value found in the OpenApi specification, RestConfiguration. Can be overridden in endpoint"
                    + " configuration.",
            label = "producer")
    private String host;

    @Metadata(
            description =
                    "What payload type this component capable of consuming. Could be one type, like `application/json`"
                            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
                            + " to the value of `Accept` HTTP header. If set overrides any value found in the OpenApi specification."
                            + " Can be overridden in endpoint configuration",
            label = "producer,advanced")
    private String consumes;

    @Metadata(
            description = "What payload type this component is producing. For example `application/json`"
                    + " according to the RFC7231. This equates to the value of `Content-Type` HTTP header. If set overrides"
                    + " any value present in the OpenApi specification. Can be overridden in endpoint configuration.",
            label = "producer,advanced")
    private String produces;

    @Metadata(
            label = "consumer,advanced",
            description =
                    "Package name to use as base (offset) for classpath scanning of POJO classes are located when using binding mode is enabled for JSon or XML. Multiple package names can be separated by comma.")
    private String bindingPackageScan;

    @Metadata(
            label = "consumer",
            description =
                    "Whether to enable validation of the client request to check if the incoming request is valid according to the OpenAPI specification")
    private boolean clientRequestValidation;

    @Metadata(
            label = "consumer",
            description =
                    "Whether to enable validation of the client request to check if the outgoing response from Camel is valid according to the OpenAPI specification")
    private boolean clientResponseValidation;

    @Metadata(
            label = "producer",
            description = "Enable validation of requests against the configured OpenAPI specification")
    private boolean requestValidationEnabled;

    @Metadata(
            description =
                    "Whether the consumer should fail,ignore or return a mock response for OpenAPI operations that are not mapped to a corresponding route.",
            label = "consumer",
            enums = "fail,ignore,mock",
            defaultValue = "fail")
    private String missingOperation;

    @Metadata(
            description =
                    "Used for inclusive filtering of mock data from directories. The pattern is using Ant-path style pattern."
                            + " Multiple patterns can be specified separated by comma.",
            label = "consumer,advanced",
            defaultValue = "classpath:camel-mock/**")
    private String mockIncludePattern = "classpath:camel-mock/**";

    @Metadata(label = "consumer", description = "Sets the context-path to use for servicing the OpenAPI specification")
    private String apiContextPath;

    @Metadata(
            description = "To use a custom strategy for how to process Rest DSL requests",
            label = "consumer,advanced")
    private RestOpenapiProcessorStrategy restOpenapiProcessorStrategy;

    @Metadata(description = "Enable usage of global SSL context parameters.", label = "security")
    private boolean useGlobalSslContextParameters;

    @Metadata(
            description =
                    "Customize TLS parameters used by the component. If not set defaults to the TLS parameters set in the Camel context ",
            label = "security")
    private SSLContextParameters sslContextParameters;

    public RestOpenApiComponent() {}

    public RestOpenApiComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {
        RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(uri, remaining, this, parameters);
        endpoint.setApiContextPath(getApiContextPath());
        endpoint.setBasePath(getBasePath());
        endpoint.setBindingPackageScan(getBindingPackageScan());
        endpoint.setClientRequestValidation(isClientRequestValidation());
        endpoint.setClientResponseValidation(isClientResponseValidation());
        endpoint.setComponentName(getComponentName());
        endpoint.setConsumerComponentName(getConsumerComponentName());
        endpoint.setConsumes(getConsumes());
        if (getHost() != null) {
            endpoint.setHost(getHost());
        }
        endpoint.setProduces(getProduces());
        endpoint.setRequestValidationEnabled(isRequestValidationEnabled());
        if (getSpecificationUri() != null) {
            endpoint.setSpecificationUri(getSpecificationUri());
        }
        endpoint.setMissingOperation(getMissingOperation());
        endpoint.setMockIncludePattern(getMockIncludePattern());
        endpoint.setRestOpenapiProcessorStrategy(getRestOpenapiProcessorStrategy());
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (bindingPackageScan == null) {
            // prioritize use rest configuration
            String base = getCamelContext().getRestConfiguration().getBindingPackageScan();
            if (base == null) {
                // over general base package from camel context
                base = getCamelContext().getCamelContextExtension().getBasePackageScan();
            }
            bindingPackageScan = base;
        }
        if (restOpenapiProcessorStrategy == null) {
            restOpenapiProcessorStrategy = new DefaultRestOpenapiProcessorStrategy();
            CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());
        }
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

    public String getProduces() {
        return produces;
    }

    public String getSpecificationUri() {
        return specificationUri;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
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

    public String getMockIncludePattern() {
        return mockIncludePattern;
    }

    public void setMockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
    }

    public String getApiContextPath() {
        return apiContextPath;
    }

    public void setApiContextPath(String apiContextPath) {
        this.apiContextPath = apiContextPath;
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
        this.consumes = isMediaRange(consumes, "consumes");
    }

    public void setHost(final String host) {
        this.host = isHostParam(host);
    }

    public void setProduces(final String produces) {
        this.produces = isMediaRange(produces, "produces");
    }

    public void setSpecificationUri(String specificationUri) {
        this.specificationUri = specificationUri;
    }

    public void setSslContextParameters(final SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(final boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public void setRequestValidationEnabled(boolean requestValidationEnabled) {
        this.requestValidationEnabled = requestValidationEnabled;
    }

    public boolean isRequestValidationEnabled() {
        return this.requestValidationEnabled;
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

    public String getBindingPackageScan() {
        return bindingPackageScan;
    }

    public void setBindingPackageScan(String bindingPackageScan) {
        this.bindingPackageScan = bindingPackageScan;
    }
}
