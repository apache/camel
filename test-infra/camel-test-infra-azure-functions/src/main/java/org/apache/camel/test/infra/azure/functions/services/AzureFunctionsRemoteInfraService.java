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
package org.apache.camel.test.infra.azure.functions.services;

import com.azure.core.management.AzureEnvironment;

/**
 * Azure Functions on Azure, with the subscription and the resource group given to the integration tests of
 * camel-azure-functions.
 */
public class AzureFunctionsRemoteInfraService implements AzureFunctionsInfraService {
    public static final String SUBSCRIPTION_ID_PROPERTY = "camel.component.azure-functions.subscription-id";
    public static final String RESOURCE_GROUP_PROPERTY = "camel.component.azure-functions.resource-group";

    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {

    }

    @Override
    public String resourceManagerEndpoint() {
        return AzureEnvironment.AZURE.getResourceManagerEndpoint();
    }

    @Override
    public String subscriptionId() {
        return System.getProperty(SUBSCRIPTION_ID_PROPERTY);
    }

    @Override
    public String resourceGroup() {
        return System.getProperty(RESOURCE_GROUP_PROPERTY);
    }

    @Override
    public String functionsEndpoint() {
        // each function app has its own host, https://<app>.azurewebsites.net
        return null;
    }
}
