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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Azure Functions component for invoking and managing Azure Functions.
 */
@Component("azure-functions")
public class FunctionsComponent extends DefaultComponent {

    @Metadata
    private FunctionsConfiguration configuration = new FunctionsConfiguration();

    public FunctionsComponent() {
    }

    public FunctionsComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("A function app name must be specified.");
        }

        final FunctionsConfiguration config = this.configuration != null
                ? this.configuration.copy()
                : new FunctionsConfiguration();

        // Parse remaining: functionApp or functionApp/functionName
        String[] parts = remaining.split("/", 2);
        config.setFunctionApp(parts[0]);
        if (parts.length > 1 && !parts[1].isBlank()) {
            config.setFunctionName(parts[1]);
        }

        final FunctionsEndpoint endpoint = new FunctionsEndpoint(uri, this, config);
        setProperties(endpoint, parameters);

        validateConfiguration(endpoint.getConfiguration());

        return endpoint;
    }

    private void validateConfiguration(FunctionsConfiguration config) {
        FunctionsOperations operation = config.getOperation();

        if (operation == FunctionsOperations.invokeFunction) {
            // For invocation, function name is required
            if (ObjectHelper.isEmpty(config.getFunctionName())) {
                throw new IllegalArgumentException(
                        "Function name is required for invokeFunction operation. "
                                                   + "Use URI format: azure-functions:functionApp/functionName");
            }
        } else {
            // For management operations, subscription ID and resource group are required
            if (ObjectHelper.isEmpty(config.getSubscriptionId())) {
                throw new IllegalArgumentException(
                        "Subscription ID is required for management operations. "
                                                   + "Set the subscriptionId parameter.");
            }
            if (ObjectHelper.isEmpty(config.getResourceGroup())) {
                throw new IllegalArgumentException(
                        "Resource group is required for management operations. "
                                                   + "Set the resourceGroup parameter.");
            }
        }
    }

    public FunctionsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(FunctionsConfiguration configuration) {
        this.configuration = configuration;
    }
}
