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

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

import ${package}.api.${name}FileHello;
import ${package}.api.${name}JavadocHello;
import ${package}.internal.${name}ApiCollection;
import ${package}.internal.${name}ApiName;
import ${package}.internal.${name}Constants;
import ${package}.internal.${name}PropertiesHelper;

/**
 * Represents a ${name} endpoint.
 */
@UriEndpoint(firstVersion = "${version}", scheme = "${scheme}", title = "${name}", syntax="${scheme}:name", 
             consumerClass = ${name}Consumer.class, label = "custom")
public class ${name}Endpoint extends AbstractApiEndpoint<${name}ApiName, ${name}Configuration> {

    @UriPath @Metadata(required = "true")
    private String name;

    // TODO create and manage API proxy
    private Object apiProxy;

    public ${name}Endpoint(String uri, ${name}Component component,
                         ${name}ApiName apiName, String methodName, ${name}Configuration endpointConfiguration) {
        super(uri, component, apiName, methodName, ${name}ApiCollection.getCollection().getHelper(apiName), endpointConfiguration);

    }

    public Producer createProducer() throws Exception {
        return new ${name}Producer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final ${name}Consumer consumer = new ${name}Consumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<${name}Configuration> getPropertiesHelper() {
        return ${name}PropertiesHelper.getHelper();
    }

    protected String getThreadProfileName() {
        return ${name}Constants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        // TODO create API proxy, set connection properties, etc.
        switch (apiName) {
            case HELLO_FILE:
                apiProxy = new ${name}FileHello();
                break;
            case HELLO_JAVADOC:
                apiProxy = new ${name}JavadocHello();
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }

    /**
     * Some description of this option, and what it does
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


}
