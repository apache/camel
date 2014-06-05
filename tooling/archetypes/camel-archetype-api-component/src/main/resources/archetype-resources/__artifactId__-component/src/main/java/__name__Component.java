## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IntrospectionSupport;

import ${package}.internal.${name}ApiCollection;
import ${package}.internal.${name}ApiName;

/**
 * Represents the component that manages {@link ${name}Endpoint}.
 */
@UriEndpoint(scheme = "${scheme}", consumerClass = ${name}Consumer.class, consumerPrefix = "consumer")
public class ${name}Component extends UriEndpointComponent {

    @UriParam
    private ${name}Configuration configuration;

    private final ${name}ApiCollection collection = ${name}ApiCollection.getCollection();

    public ${name}Component() {
        super(${name}Endpoint.class);
    }

    public ${name}Component(CamelContext context) {
        super(context, ${name}Endpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // split remaining path to get API name and method
        final String[] pathElements = remaining.split("/");
        String apiNameStr;
        String methodName;
        switch (pathElements.length) {
            case 1:
                apiNameStr = "";
                methodName = pathElements[0];
                break;
            case 2:
                apiNameStr = pathElements[0];
                methodName = pathElements[1];
                break;
            default:
                throw new CamelException("Invalid URI path [" + remaining +
                "], must be of the format " + collection.getApiNames() + "/<operation-apiName>");
        }
        // get API enum from apiName string
        final ${name}ApiName apiName;
        try {
            apiName = ${name}ApiName.fromValue(apiNameStr);
        } catch (IllegalArgumentException e) {
            throw new CamelException("Invalid URI path prefix [" + remaining +
                "], must be one of " + collection.getApiNames());
        }

        final ${name}Configuration endpointConfiguration = createEndpointConfiguration(apiName);
        final Endpoint endpoint = new ${name}Endpoint(uri, this, apiName, methodName, endpointConfiguration);

        // set endpoint property inBody
        setProperties(endpoint, parameters);

        // configure endpoint properties and initialize state
        endpoint.configureProperties(parameters);

        return endpoint;
    }

    private ${name}Configuration createEndpointConfiguration(${name}ApiName name) throws Exception {
        final Map<String, Object> componentProperties = new HashMap<String, Object>();
        if (configuration != null) {
            IntrospectionSupport.getProperties(configuration, componentProperties, null, false);
        }

        // create endpoint configuration with component properties
        final ${name}Configuration endpointConfiguration = collection.getEndpointConfiguration(name);
        IntrospectionSupport.setProperties(endpointConfiguration, componentProperties);
        return endpointConfiguration;
    }

    public ${name}Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(${name}Configuration configuration) {
        this.configuration = configuration;
    }
}
