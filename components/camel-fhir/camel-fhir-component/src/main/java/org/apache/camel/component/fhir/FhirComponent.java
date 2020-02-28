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

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirApiName;
import org.apache.camel.component.fhir.internal.FhirHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link FhirEndpoint}.
 */
@Component("fhir")
public class FhirComponent extends AbstractApiComponent<FhirApiName, FhirConfiguration, FhirApiCollection> {

    @Metadata(label = "advanced")
    FhirConfiguration configuration;

    @Metadata(label = "advanced")
    private IGenericClient client;

    public FhirComponent() {
        super(FhirEndpoint.class, FhirApiName.class, FhirApiCollection.getCollection());
    }

    public FhirComponent(CamelContext context) {
        super(context, FhirEndpoint.class, FhirApiName.class, FhirApiCollection.getCollection());
    }

    @Override
    protected FhirApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return FhirApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, FhirApiName apiName,
                                      FhirConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new FhirEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    public IGenericClient getClient(FhirConfiguration endpointConfiguration) {
        final IGenericClient result;
        if (endpointConfiguration.equals(this.configuration)) {
            synchronized (this) {
                if (client == null) {
                    client = FhirHelper.createClient(this.configuration, getCamelContext());
                }
            }
            result = client;
        } else {
            result = FhirHelper.createClient(endpointConfiguration, getCamelContext());
        }
        return result;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(FhirConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public FhirConfiguration getConfiguration() {
        return super.getConfiguration();
    }

}

