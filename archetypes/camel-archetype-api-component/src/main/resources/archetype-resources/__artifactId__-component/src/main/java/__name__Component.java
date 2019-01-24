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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.support.component.AbstractApiComponent;

import ${package}.internal.${name}ApiCollection;
import ${package}.internal.${name}ApiName;

/**
 * Represents the component that manages {@link ${name}Endpoint}.
 */
public class ${name}Component extends AbstractApiComponent<${name}ApiName, ${name}Configuration, ${name}ApiCollection> {

    public ${name}Component() {
        super(${name}Endpoint.class, ${name}ApiName.class, ${name}ApiCollection.getCollection());
    }

    public ${name}Component(CamelContext context) {
        super(context, ${name}Endpoint.class, ${name}ApiName.class, ${name}ApiCollection.getCollection());
    }

    @Override
    protected ${name}ApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return ${name}ApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, ${name}ApiName apiName,
                                      ${name}Configuration endpointConfiguration) {
        ${name}Endpoint endpoint = new ${name}Endpoint(uri, this, apiName, methodName, endpointConfiguration);
        endpoint.setName(methodName);
        return endpoint;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(${name}Configuration configuration) {
        super.setConfiguration(configuration);
    }

}
