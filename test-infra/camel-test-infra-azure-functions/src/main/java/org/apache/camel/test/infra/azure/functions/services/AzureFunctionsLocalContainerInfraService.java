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

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzContainer;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;

/**
 * A local Azure Functions service: Floci AZ, with its Functions service in mocked mode, so it needs no Docker socket
 * and no function runtime image. Its Azure Resource Manager endpoint manages the function apps as
 * {@code Microsoft.Web/sites} resources, and an invocation of a deployed function answers a stub instead of running it.
 * <p>
 * Floci AZ accepts any subscription, any resource group and any bearer token: {@code AzureFunctionsClientUtils} builds
 * an {@code AppServiceManager} for it.
 */
@InfraService(service = AzureFunctionsInfraService.class,
              description = "Local Azure Functions service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "functions")
public class AzureFunctionsLocalContainerInfraService extends FlociAzInfraService implements AzureFunctionsInfraService {
    /**
     * The subscription in the directory of Floci AZ
     */
    public static final String DEFAULT_SUBSCRIPTION_ID = "00000000-0000-0000-0000-000000000001";
    /**
     * The resource group of the function apps. The service does not create it: by default Floci AZ also creates a
     * resource in a group that does not exist.
     */
    public static final String DEFAULT_RESOURCE_GROUP = "camel-functions";

    @Override
    protected FlociAzContainer initContainer(String imageName) {
        return super.initContainer(imageName)
                .withEnv("FLOCI_AZ_SERVICES_FUNCTIONS_MOCKED", "true");
    }

    @Override
    public String resourceManagerEndpoint() {
        return getContainer().serviceAddress() + "/";
    }

    @Override
    public String subscriptionId() {
        return DEFAULT_SUBSCRIPTION_ID;
    }

    @Override
    public String resourceGroup() {
        return DEFAULT_RESOURCE_GROUP;
    }

    /**
     * The Functions service of the storage account: its admin API is under {@code /admin/apps}, and a function is
     * invoked on {@code /api/<app>/<function>}
     */
    @Override
    public String functionsEndpoint() {
        return getContainer().serviceAddress() + "/" + FlociAzContainer.DEFAULT_ACCOUNT_NAME + "-functions";
    }
}
