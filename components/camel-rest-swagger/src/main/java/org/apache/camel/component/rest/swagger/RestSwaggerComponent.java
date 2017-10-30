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

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestProducerFactory;

import static org.apache.camel.component.rest.swagger.RestSwaggerHelper.isHostParam;
import static org.apache.camel.component.rest.swagger.RestSwaggerHelper.isMediaRange;
import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * An awesome REST component backed by Swagger specifications. Creates endpoints
 * that connect to REST APIs defined by Swagger specification. This component
 * delegates to other {@link RestProducerFactory} components to act as REST
 * clients, but it configures them from Swagger specification. Client needs to
 * point to operation that it wants to invoke via REST, provide any additional
 * HTTP headers as headers in the Camel message, and any payload as the body of
 * the incoming message.
 * <p>
 * Example usage using Java DSL:
 * <p>
 *
 * <pre>
 * from(...).to("rest-swagger:http://petstore.swagger.io/v2/swagger.json#getPetById")
 * </pre>
 *
 * This relies on only one {@link RestProducerFactory} component being available
 * to Camel, you can use specific, for instance preconfigured component by using
 * the {@code componentName} endpoint property. For example using Undertow
 * component in Java DSL:
 * <p>
 *
 * <pre>
 * Component undertow = new UndertowComponent();
 * undertow.setSslContextParameters(...);
 * //...
 * camelContext.addComponent("myUndertow", undertow);
 *
 * from(...).to("rest-swagger:http://petstore.swagger.io/v2/swagger.json#getPetById?componentName=myUndertow")
 * </pre>
 *
 * The most concise way of using this component would be to define it in the
 * Camel context under a meaningful name, for example:
 *
 * <pre>
 * Component petstore = new RestSwaggerComponent();
 * petstore.setSpecificationUri("http://petstore.swagger.io/v2/swagger.json");
 * petstore.setComponentName("undertow");
 * //...
 * camelContext.addComponent("petstore", petstore);
 *
 * from(...).to("petstore:getPetById")
 * </pre>
 */
public final class RestSwaggerComponent extends DefaultComponent {
    public static final String DEFAULT_BASE_PATH = "/";

    static final URI DEFAULT_SPECIFICATION_URI = URI.create(RestSwaggerComponent.DEFAULT_SPECIFICATION_URI_STR);

    static final String DEFAULT_SPECIFICATION_URI_STR = "swagger.json";

    @Metadata(
        description = "API basePath, for example \"`/v2`\". Default is unset, if set overrides the value present in Swagger specification.",
        defaultValue = "", label = "producer", required = "false")
    private String basePath = "";

    @Metadata(description = "Name of the Camel component that will perform the requests. The compnent must be present"
        + " in Camel registry and it must implement RestProducerFactory service provider interface. If not set"
        + " CLASSPATH is searched for single component that implements RestProducerFactory SPI. Can be overriden in"
        + " endpoint configuration.", label = "producer", required = "false")
    private String componentName;

    @Metadata(
        description = "What payload type this component capable of consuming. Could be one type, like `application/json`"
            + " or multiple types as `application/json, application/xml; q=0.5` according to the RFC7231. This equates"
            + " to the value of `Accept` HTTP header. If set overrides any value found in the Swagger specification."
            + " Can be overriden in endpoint configuration",
        label = "producer", required = "false")
    private String consumes;

    @Metadata(description = "Scheme hostname and port to direct the HTTP requests to in the form of"
        + " `http[s]://hostname[:port]`. Can be configured at the endpoint, component or in the correspoding"
        + " REST configuration in the Camel Context. If you give this component a name (e.g. `petstore`) that"
        + " REST configuration is consulted first, `rest-swagger` next, and global configuration last. If set"
        + " overrides any value found in the Swagger specification, RestConfiguration. Can be overriden in endpoint"
        + " configuration.", label = "producer", required = "false")
    private String host;

    @Metadata(
        description = "What payload type this component is producing. For example `application/json`"
            + " according to the RFC7231. This equates to the value of `Content-Type` HTTP header. If set overrides"
            + " any value present in the Swagger specification. Can be overriden in endpoint configuration.",
        label = "producer", required = "false")
    private String produces;

    @Metadata(description = "Path to the Swagger specification file. The scheme, host base path are taken from this"
        + " specification, but these can be overriden with properties on the component or endpoint level. If not"
        + " given the component tries to load `swagger.json` resource. Note that the `host` defined on the"
        + " component and endpoint of this Component should contain the scheme, hostname and optionally the"
        + " port in the URI syntax (i.e. `https://api.example.com:8080`). Can be overriden in endpoint"
        + " configuration.", defaultValue = DEFAULT_SPECIFICATION_URI_STR, label = "producer", required = "false")
    private URI specificationUri;

    public RestSwaggerComponent() {
    }

    public RestSwaggerComponent(final CamelContext context) {
        super(context);
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

    public String getProduces() {
        return produces;
    }

    public URI getSpecificationUri() {
        return specificationUri;
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

    public void setProduces(final String produces) {
        this.produces = isMediaRange(produces, "produces");
    }

    public void setSpecificationUri(final URI specificationUri) {
        this.specificationUri = notNull(specificationUri, "specificationUri");
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
        throws Exception {
        final Endpoint endpoint = new RestSwaggerEndpoint(uri, remaining, this, parameters);

        setProperties(endpoint, parameters);

        return endpoint;
    }

}
