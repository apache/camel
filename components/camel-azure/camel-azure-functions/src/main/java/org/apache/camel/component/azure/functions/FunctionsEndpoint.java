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
package org.apache.camel.component.azure.functions;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.functions.client.FunctionsClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Invoke and manage Azure Functions.
 */
@UriEndpoint(firstVersion = "4.19.0",
             scheme = "azure-functions",
             title = "Azure Functions",
             syntax = "azure-functions:functionApp/functionName",
             category = { Category.CLOUD, Category.SERVERLESS },
             producerOnly = true,
             headersClass = FunctionsConstants.class)
public class FunctionsEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private FunctionsConfiguration configuration;

    private FunctionsClientFactory clientFactory;

    public FunctionsEndpoint(String uri, Component component, FunctionsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FunctionsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported for azure-functions component");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        clientFactory = new FunctionsClientFactory();
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getFunctionApp())) {
            return String.format("https://%s.azurewebsites.net", configuration.getFunctionApp());
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "azure-functions";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        Map<String, String> metadata = new HashMap<>();
        if (ObjectHelper.isNotEmpty(configuration.getFunctionApp())) {
            metadata.put("functionApp", configuration.getFunctionApp());
        }
        if (ObjectHelper.isNotEmpty(configuration.getFunctionName())) {
            metadata.put("functionName", configuration.getFunctionName());
        }
        if (ObjectHelper.isNotEmpty(configuration.getResourceGroup())) {
            metadata.put("resourceGroup", configuration.getResourceGroup());
        }
        return metadata;
    }

    public FunctionsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(FunctionsConfiguration configuration) {
        this.configuration = configuration;
    }

    public FunctionsClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(FunctionsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
}
