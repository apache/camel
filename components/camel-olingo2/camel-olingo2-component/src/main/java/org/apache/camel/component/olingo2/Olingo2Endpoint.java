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
package org.apache.camel.component.olingo2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.olingo2.internal.Olingo2ApiCollection;
import org.apache.camel.component.olingo2.internal.Olingo2ApiName;
import org.apache.camel.component.olingo2.internal.Olingo2Constants;
import org.apache.camel.component.olingo2.internal.Olingo2PropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * Communicates with OData 2.0 services using Apache Olingo.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "olingo2", title = "Olingo2", syntax = "olingo2:apiName/methodName", consumerClass = Olingo2Consumer.class, label = "cloud")
public class Olingo2Endpoint extends AbstractApiEndpoint<Olingo2ApiName, Olingo2Configuration> {

    protected static final String RESOURCE_PATH_PROPERTY = "resourcePath";
    protected static final String RESPONSE_HANDLER_PROPERTY = "responseHandler";

    private static final String KEY_PREDICATE_PROPERTY = "keyPredicate";
    private static final String QUERY_PARAMS_PROPERTY = "queryParams";
    private static final String ENDPOINT_HTTP_HEADERS_PROPERTY = "endpointHttpHeaders";

    private static final String READ_METHOD = "read";
    private static final String EDM_PROPERTY = "edm";
    private static final String DATA_PROPERTY = "data";
    private static final String DELETE_METHOD = "delete";

    // unparsed variants
    private static final String UREAD_METHOD = "uread";

    private final Set<String> endpointPropertyNames;

    @UriParam
    private Olingo2Configuration configuration;

    private Olingo2AppWrapper apiProxy;

    public Olingo2Endpoint(String uri, Olingo2Component component,
                           Olingo2ApiName apiName, String methodName, Olingo2Configuration endpointConfiguration) {
        super(uri, component, apiName, methodName, Olingo2ApiCollection.getCollection().getHelper(apiName), endpointConfiguration);

        this.configuration = endpointConfiguration;

        // get all endpoint property names
        endpointPropertyNames = new HashSet<String>(getPropertiesHelper().getValidEndpointProperties(configuration));
        // avoid adding edm as queryParam
        endpointPropertyNames.add(EDM_PROPERTY);
        endpointPropertyNames.add(ENDPOINT_HTTP_HEADERS_PROPERTY);
    }

    public Producer createProducer() throws Exception {
        return new Olingo2Producer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        // only read method is supported
        if (!READ_METHOD.equals(methodName) && !UREAD_METHOD.equals(methodName)) {
            throw new IllegalArgumentException("Only read method is supported for consumer endpoints");
        }
        final Olingo2Consumer consumer = new Olingo2Consumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<Olingo2Configuration> getPropertiesHelper() {
        return Olingo2PropertiesHelper.getHelper();
    }

    protected String getThreadProfileName() {
        return Olingo2Constants.THREAD_PROFILE_NAME;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        // handle individual query params
        parseQueryParams(options);

        super.configureProperties(options);
    }

    @Override
    protected void afterConfigureProperties() {
        // set default inBody
        if (!(READ_METHOD.equals(methodName) || DELETE_METHOD.equals(methodName) || UREAD_METHOD.equals(methodName))
            && inBody == null) {
            inBody = DATA_PROPERTY;
        }
        createProxy();
    }

    @Override
    public synchronized Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy.getOlingo2App();
    }

    @Override
    public Olingo2Component getComponent() {
        return (Olingo2Component) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        if (apiProxy == null) {
            createProxy();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (apiProxy != null) {
            // close the apiProxy
            getComponent().closeApiProxy(apiProxy);
            apiProxy = null;
        }
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        // add edm, and responseHandler property names
        // edm is computed on first call to getApiProxy(), and responseHandler is provided by consumer and producer
        if (!DELETE_METHOD.equals(methodName)) {
            propertyNames.add(EDM_PROPERTY);
        }
        propertyNames.add(RESPONSE_HANDLER_PROPERTY);
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {

        // read Edm if not set yet
        properties.put(EDM_PROPERTY, apiProxy.getEdm());

        // handle keyPredicate
        final String keyPredicate = (String) properties.get(KEY_PREDICATE_PROPERTY);
        if (keyPredicate != null) {

            // make sure a resource path is provided
            final String resourcePath = (String) properties.get(RESOURCE_PATH_PROPERTY);
            if (resourcePath == null) {
                throw new IllegalArgumentException("Resource path must be provided in endpoint URI, or URI parameter '"
                    + RESOURCE_PATH_PROPERTY + "', or exchange header '"
                    + Olingo2Constants.PROPERTY_PREFIX + RESOURCE_PATH_PROPERTY + "'");
            }

            // append keyPredicate to dynamically create resource path
            properties.put(RESOURCE_PATH_PROPERTY, resourcePath + '(' + keyPredicate + ')');
        }

        // handle individual queryParams
        parseQueryParams(properties);
    }

    private void createProxy() {
        apiProxy = getComponent().createApiProxy(getConfiguration());
    }

    private void parseQueryParams(Map<String, Object> options) {
        // extract non-endpoint properties as query params
        final Map<String, String> queryParams = new HashMap<String, String>();
        for (Iterator<Map.Entry<String, Object>> it = options.entrySet().iterator(); it.hasNext();) {

            final Map.Entry<String, Object> entry = it.next();
            final String paramName = entry.getKey();

            if (!endpointPropertyNames.contains(paramName)) {

                // add to query params
                final Object value = entry.getValue();
                if (value == null) {
                    throw new IllegalArgumentException("Null value for query parameter " + paramName);
                }
                queryParams.put(paramName, value.toString());

                // remove entry from supplied options
                it.remove();
            }
        }
        if (!queryParams.isEmpty()) {

            @SuppressWarnings("unchecked")
            final Map<String, String> oldParams = (Map<String, String>) options.get(QUERY_PARAMS_PROPERTY);
            if (oldParams == null) {
                // set queryParams property
                options.put(QUERY_PARAMS_PROPERTY, queryParams);
            } else {
                // overwrite old params in supplied map
                oldParams.putAll(queryParams);
            }

        }
    }
}
