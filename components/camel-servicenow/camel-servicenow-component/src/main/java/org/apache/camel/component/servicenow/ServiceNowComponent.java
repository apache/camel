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
package org.apache.camel.component.servicenow;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

/**
 * Represents the component that manages {@link ServiceNowEndpoint}.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("servicenow")
public class ServiceNowComponent extends DefaultComponent implements SSLContextParametersAware {
    @Metadata(label = "advanced")
    private String instanceName;
    @Metadata
    private ServiceNowConfiguration configuration;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public ServiceNowComponent() {
        this(null);
    }

    public ServiceNowComponent(CamelContext camelContext) {
        super(camelContext);

        this.configuration = new ServiceNowConfiguration();

        registerExtension(ServiceNowComponentVerifierExtension::new);
        registerExtension(ServiceNowMetaDataExtension::new);
    }

    // ****************************************
    // Properties
    // ****************************************

    public String getInstanceName() {
        return instanceName;
    }

    /**
     * The ServiceNow instance name
     */
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public ServiceNowConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(ServiceNowConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public ComponentVerifierExtension getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }

    // ****************************************
    // Component impl
    // ****************************************

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final CamelContext context = getCamelContext();
        final ServiceNowConfiguration configuration = this.configuration.copy();

        Map<String, Object> models = PropertiesHelper.extractProperties(parameters, "model.");
        for (Map.Entry<String, Object> entry : models.entrySet()) {
            configuration.addModel(
                entry.getKey(),
                EndpointHelper.resolveParameter(context, (String)entry.getValue(), Class.class));
        }

        Map<String, Object> requestModels = PropertiesHelper.extractProperties(parameters, "requestModel.");
        for (Map.Entry<String, Object> entry : requestModels.entrySet()) {
            configuration.addRequestModel(
                entry.getKey(),
                EndpointHelper.resolveParameter(context, (String)entry.getValue(), Class.class));
        }

        Map<String, Object> responseModels = PropertiesHelper.extractProperties(parameters, "responseModel.");
        for (Map.Entry<String, Object> entry : responseModels.entrySet()) {
            configuration.addResponseModel(
                entry.getKey(),
                EndpointHelper.resolveParameter(context, (String)entry.getValue(), Class.class));
        }

        if (ObjectHelper.isEmpty(remaining)) {
            // If an instance is not set on the endpoint uri, use the one set on component.
            remaining = instanceName;
        }

        String instanceName = getCamelContext().resolvePropertyPlaceholders(remaining);
        ServiceNowEndpoint endpoint = new ServiceNowEndpoint(uri, this, configuration, instanceName);
        setProperties(endpoint, parameters);

        if (!configuration.hasApiUrl()) {
            configuration.setApiUrl(String.format("https://%s.service-now.com/api", instanceName));
        }
        if (!configuration.hasOauthTokenUrl()) {
            configuration.setOauthTokenUrl(String.format("https://%s.service-now.com/oauth_token.do", instanceName));
        }
        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

}
