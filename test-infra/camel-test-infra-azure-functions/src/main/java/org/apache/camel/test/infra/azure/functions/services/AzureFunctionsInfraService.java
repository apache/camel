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

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Azure Functions: function apps, managed through Azure Resource Manager (the {@code Microsoft.Web/sites} resources),
 * and their HTTP-triggered functions.
 */
public interface AzureFunctionsInfraService extends InfrastructureService {

    /**
     * The Azure Resource Manager endpoint the function apps are managed through, for example
     * {@code https://management.azure.com/}
     */
    String resourceManagerEndpoint();

    /**
     * The subscription of the function apps
     */
    String subscriptionId();

    /**
     * The resource group of the function apps
     */
    String resourceGroup();

    /**
     * The address the HTTP-triggered functions are invoked on, as {@code <functionsEndpoint>/api/<app>/<function>}, or
     * null when each function app has a host of its own, as on Azure ({@code https://<app>.azurewebsites.net})
     */
    String functionsEndpoint();
}
