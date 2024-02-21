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
package org.apache.camel.component.fhir;

import java.util.EnumMap;
import java.util.Map;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.fhir.api.ExtraParameters;
import org.apache.camel.component.fhir.api.FhirCapabilities;
import org.apache.camel.component.fhir.api.FhirCreate;
import org.apache.camel.component.fhir.api.FhirDelete;
import org.apache.camel.component.fhir.api.FhirHistory;
import org.apache.camel.component.fhir.api.FhirLoadPage;
import org.apache.camel.component.fhir.api.FhirMeta;
import org.apache.camel.component.fhir.api.FhirOperation;
import org.apache.camel.component.fhir.api.FhirPatch;
import org.apache.camel.component.fhir.api.FhirRead;
import org.apache.camel.component.fhir.api.FhirSearch;
import org.apache.camel.component.fhir.api.FhirTransaction;
import org.apache.camel.component.fhir.api.FhirUpdate;
import org.apache.camel.component.fhir.api.FhirValidate;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirApiName;
import org.apache.camel.component.fhir.internal.FhirConstants;
import org.apache.camel.component.fhir.internal.FhirPropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Exchange information in the healthcare domain using the FHIR (Fast Healthcare Interoperability Resources) standard.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "fhir", title = "FHIR", syntax = "fhir:apiName/methodName",
             apiSyntax = "apiName/methodName",
             category = { Category.API })
public class FhirEndpoint extends AbstractApiEndpoint<FhirApiName, FhirConfiguration> {

    private static final String EXTRA_PARAMETERS_PROPERTY = "extraParameters";

    private Object apiProxy;

    @UriParam
    private FhirConfiguration configuration;

    public FhirEndpoint(String uri, FhirComponent component,
                        FhirApiName apiName, String methodName, FhirConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, FhirApiCollection.getCollection().getHelper(apiName), endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FhirProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final FhirConsumer consumer = new FhirConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<FhirConfiguration> getPropertiesHelper() {
        return FhirPropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return FhirConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        IGenericClient client = getClient();
        switch (apiName) {
            case CAPABILITIES:
                apiProxy = new FhirCapabilities(client);
                break;
            case CREATE:
                apiProxy = new FhirCreate(client);
                break;
            case DELETE:
                apiProxy = new FhirDelete(client);
                break;
            case HISTORY:
                apiProxy = new FhirHistory(client);
                break;
            case LOAD_PAGE:
                apiProxy = new FhirLoadPage(client);
                break;
            case META:
                apiProxy = new FhirMeta(client);
                break;
            case OPERATION:
                apiProxy = new FhirOperation(client);
                break;
            case PATCH:
                apiProxy = new FhirPatch(client);
                break;
            case READ:
                apiProxy = new FhirRead(client);
                break;
            case SEARCH:
                apiProxy = new FhirSearch(client);
                break;
            case TRANSACTION:
                apiProxy = new FhirTransaction(client);
                break;
            case UPDATE:
                apiProxy = new FhirUpdate(client);
                break;
            case VALIDATE:
                apiProxy = new FhirValidate(client);
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {
        Map<ExtraParameters, Object> extraProperties = getExtraParameters(properties);
        for (ExtraParameters extraParameter : ExtraParameters.values()) {
            Object value = properties.get(extraParameter.getParam());
            if (value != null) {
                extraProperties.put(extraParameter, value);
            }
        }
        properties.put(EXTRA_PARAMETERS_PROPERTY, extraProperties);

    }

    public IGenericClient getClient() {
        return configuration.getClient();
    }

    private Map<ExtraParameters, Object> getExtraParameters(Map<String, Object> properties) {
        Object extraParameters = properties.get(EXTRA_PARAMETERS_PROPERTY);
        if (extraParameters == null) {
            return new EnumMap<>(ExtraParameters.class);
        }
        return (Map<ExtraParameters, Object>) extraParameters;
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }
}
