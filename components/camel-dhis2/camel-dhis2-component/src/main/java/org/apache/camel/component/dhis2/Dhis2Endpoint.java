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
package org.apache.camel.component.dhis2;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dhis2.api.Dhis2Delete;
import org.apache.camel.component.dhis2.api.Dhis2Get;
import org.apache.camel.component.dhis2.api.Dhis2Post;
import org.apache.camel.component.dhis2.api.Dhis2Put;
import org.apache.camel.component.dhis2.api.Dhis2ResourceTables;
import org.apache.camel.component.dhis2.internal.Dhis2ApiCollection;
import org.apache.camel.component.dhis2.internal.Dhis2ApiName;
import org.apache.camel.component.dhis2.internal.Dhis2Constants;
import org.apache.camel.component.dhis2.internal.Dhis2PropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;

/**
 * Leverages the DHIS2 Java SDK to integrate Apache Camel with the DHIS2 Web API.
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = "dhis2", title = "DHIS2", syntax = "dhis2:apiName/methodName",
             apiSyntax = "apiName/methodName", category = { Category.API })
public class Dhis2Endpoint extends AbstractApiEndpoint<Dhis2ApiName, Dhis2Configuration> {

    @UriParam
    private final Dhis2Configuration configuration;

    private Object apiProxy;

    public Dhis2Endpoint(String uri, Dhis2Component component,
                         Dhis2ApiName apiName, String methodName, Dhis2Configuration endpointConfiguration) {
        super(uri, component, apiName, methodName, Dhis2ApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    public Producer createProducer() throws Exception {
        return new Dhis2Producer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final Dhis2Consumer consumer = new Dhis2Consumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<Dhis2Configuration> getPropertiesHelper() {
        return Dhis2PropertiesHelper.getHelper(getCamelContext());
    }

    protected String getThreadProfileName() {
        return Dhis2Constants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        Dhis2Client dhis2Client = this.getClient();
        switch (apiName) {
            case GET:
                apiProxy = new Dhis2Get(dhis2Client);
                break;
            case POST:
                apiProxy = new Dhis2Post(dhis2Client);
                break;
            case DELETE:
                apiProxy = new Dhis2Delete(dhis2Client);
                break;
            case PUT:
                apiProxy = new Dhis2Put(dhis2Client);
                break;
            case RESOURCE_TABLES:
                apiProxy = new Dhis2ResourceTables(dhis2Client);
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }

    protected Dhis2Client getClient() {
        return ((Dhis2Component) this.getComponent()).getClient(this.configuration);
    }
}
