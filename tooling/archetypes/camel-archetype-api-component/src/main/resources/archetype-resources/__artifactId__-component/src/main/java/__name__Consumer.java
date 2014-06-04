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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ${package}.internal.${name}PropertiesHelper;

/**
 * The ${name} consumer.
 */
public class ${name}Consumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(${name}Consumer.class);

    private final ${name}Endpoint endpoint;

    // helpers
    private final ${name}PropertiesHelper propertiesHelper;
    private final ApiMethodHelper methodHelper;

    // API method to invoke
    private final Enum<? extends ApiMethod> method;

    // properties used to invoke
    private final Map<String, Object> endpointProperties;

    public ${name}Consumer(${name}Endpoint endpoint, Processor processor) {
        super(endpoint, processor);

        // cache variables
        this.endpoint = endpoint;
        this.propertiesHelper = ${name}PropertiesHelper.getHelper();
        this.methodHelper = endpoint.getMethodHelper();

        // determine the consumer method to invoke
        this.method = findMethod();

        // get endpoint properties in a map
        final HashMap<String, Object> properties = new HashMap<String, Object>();
        propertiesHelper.getEndpointProperties(endpoint.getConfiguration(), properties);
        this.endpointProperties = Collections.unmodifiableMap(properties);
    }

    @Override
    public boolean isGreedy() {
        // make this consumer not greedy to avoid making too many ${name} calls
        return false;
    }

    private Enum<? extends ApiMethod> findMethod() {

        Enum<? extends ApiMethod> result;
        // find one that takes the largest subset of endpoint parameters
        final Set<String> argNames = new HashSet<String>();
        argNames.addAll(propertiesHelper.getEndpointPropertyNames(endpoint.getConfiguration()));

        final String[] argNamesArray = argNames.toArray(new String[argNames.size()]);
        List<Enum<? extends ApiMethod>> filteredMethods = methodHelper.filterMethods(
                endpoint.getCandidates(), ApiMethodHelper.MatchType.SUPER_SET, argNamesArray);

        if (filteredMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Missing properties for %s/%s, need one or more from %s",
                            endpoint.getApiName().getName(), endpoint.getMethodName(),
                            methodHelper.getMissingProperties(endpoint.getMethodName(), argNames)));
        } else if (filteredMethods.size() == 1) {
            // single match
            result = filteredMethods.get(0);
        } else {
            result = methodHelper.getHighestPriorityMethod(filteredMethods);
            LOG.warn("Using highest priority operation {} from operations {}", method, filteredMethods);
        }
        return result;
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = getMethodArguments();
        try {
            Object result = methodHelper.invokeMethod(endpoint.getApiProxy(), method, args);

            // process result according to type
            if (result != null && (result instanceof Collection || result.getClass().isArray())) {
                // create an exchange for every element
                final Object array = getResultAsArray(result);
                final int length = Array.getLength(array);
                for (int i = 0; i < length; i++) {
                    processResult(Array.get(array, i));
                }
                return length;
            } else {
                processResult(result);
                return 1; // number of messages polled
            }
        } catch (Throwable t) {
            throw ObjectHelper.wrapRuntimeCamelException(t);
        }
    }

    private void processResult(Object result) throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(result);
        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }

    private Object getResultAsArray(Object result) {
        if (result.getClass().isArray()) {
            // no conversion needed
            return result;
        }
        // must be a Collection
        Collection<?> collection = (Collection<?>) result;
        return collection.toArray(new Object[collection.size()]);
    }

    private Map<String, Object> getMethodArguments() {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.putAll(endpointProperties);

        // TODO do consumer specific argument manipulation, such as setting constants or per poll properties
        return arguments;
    }
}
