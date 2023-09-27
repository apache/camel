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

@Component("fhir")
public class FhirComponent extends AbstractApiComponent<FhirApiName, FhirConfiguration, FhirApiCollection> {

    @Metadata(label = "advanced")
    private FhirConfiguration configuration;

    public FhirComponent() {
        super(FhirApiName.class, FhirApiCollection.getCollection());
    }

    public FhirComponent(CamelContext context) {
        super(context, FhirApiName.class, FhirApiCollection.getCollection());
    }

    @Override
    protected FhirApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(FhirApiName.class, apiNameStr);
    }

    @Override
    protected void afterPropertiesSet(FhirConfiguration endpointConfiguration) {
        // ensure a client is set on the config
        if (endpointConfiguration.getClient() == null) {
            if (configuration != null && configuration.getClient() != null) {
                endpointConfiguration.setClient(configuration.getClient());

                return;
            } else if (configuration != null) {
                if (endpointConfiguration.getServerUrl() == null) {
                    endpointConfiguration.setServerUrl(configuration.getServerUrl());
                }
                if (endpointConfiguration.getFhirContext() == null) {
                    endpointConfiguration.setFhirContext(configuration.getFhirContext());
                }
            }

            endpointConfiguration.setClient(createClient(endpointConfiguration));
        }
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, FhirApiName apiName,
            FhirConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);

        return new FhirEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    protected IGenericClient createClient(FhirConfiguration config) {
        return FhirHelper.createClient(config, getCamelContext());
    }

    @Override
    public FhirConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(FhirConfiguration configuration) {
        this.configuration = configuration;
    }
}
