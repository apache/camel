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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ${package}.internal.${name}ApiCollection;
import ${package}.internal.${name}ApiName;
import ${package}.internal.${name}PropertiesHelper;

/**
 * Represents a ${name} endpoint.
 */
@UriEndpoint(scheme = "${scheme}", consumerClass = ${name}Consumer.class, consumerPrefix = "consumer")
public class ${name}Endpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(${name}Endpoint.class);

    @UriParam
    private final ${name}Configuration configuration;

    // property name for Exchange 'In' message body
    @UriParam
    private String inBody;

    // API name
    private final ${name}ApiName apiName;

    // API method name
    private final String methodName;

    // API method helper
    private final ApiMethodHelper methodHelper;

    // candidate methods based on method name and endpoint configuration
    private List<Enum<? extends ApiMethod>> candidates;

    // TODO create and manage API proxies
    private final Map<${name}ApiName, Object> proxyCache;

    public ${name}Endpoint(String uri, ${name}Component component, ${name}ApiName apiName, String methodName, ${name}Configuration endpointConfiguration) {
        super(uri, component);
        this.apiName = apiName;
        this.methodName = methodName;
        this.configuration = endpointConfiguration;

        methodHelper = ${name}ApiCollection.getCollection().getHelper(apiName);

        // TODO manage API proxies
        proxyCache = new HashMap<${name}ApiName, Object>();
        proxyCache.put(${name}ApiName.HELLO_FILE, new ${name}FileHello());
        proxyCache.put(${name}ApiName.HELLO_JAVADOC, new ${name}JavadocHello());
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

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);

        // set configuration properties first
        try {
            EndpointHelper.setReferenceProperties(getCamelContext(), configuration, options);
            EndpointHelper.setProperties(getCamelContext(), configuration, options);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        // validate and initialize state
        initState();
    }

    private void initState() {

        // get endpoint property names
        final Set<String> arguments = new HashSet<String>();
        arguments.addAll(${name}PropertiesHelper.getHelper().getEndpointPropertyNames(configuration));

        // add inBody argument for producers
        if (inBody != null) {
            arguments.add(inBody);
        }
        final String[] argNames = arguments.toArray(new String[arguments.size()]);

        // create a list of candidate methods
        candidates = new ArrayList<Enum<? extends ApiMethod>>();
        candidates.addAll(methodHelper.getCandidateMethods(methodName, argNames));

        // error if there are no candidates
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No matching method for %s/%s, with arguments %s",
                            apiName.getName(), methodName, arguments));
        }

        // log missing/extra properties for debugging
        if (LOG.isDebugEnabled()) {
            final Set<String> missing = methodHelper.getMissingProperties(methodName, arguments);
            if (!missing.isEmpty()) {
                LOG.debug("Method {} could use one or more properties from {}", methodName, missing);
            }
        }
    }

    @Override
    public ${name}Component getComponent() {
        return (${name}Component) super.getComponent();
    }

    public ${name}Configuration getConfiguration() {
        return configuration;
    }

    public ${name}ApiName getApiName() {
        return apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    public ApiMethodHelper getMethodHelper() {
        return methodHelper;
    }

    public List<Enum<? extends ApiMethod>> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public String getInBody() {
        return inBody;
    }

    public void setInBody(String inBody) {
        // validate property name
        ObjectHelper.notNull(inBody, "inBody");
        if (!${name}PropertiesHelper.getHelper().getValidEndpointProperties(configuration).contains(inBody)) {
            throw new IllegalArgumentException("Unknown property " + inBody);
        }
        this.inBody = inBody;
    }

    public Object getApiProxy() {
        // TODO manage API proxies
        return proxyCache.get(apiName);
    }
}
