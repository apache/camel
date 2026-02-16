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
package org.apache.camel.component.azure.functions.integration;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.functions.FunctionsConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Azure Functions management operations.
 * <p>
 * These tests require Azure AD authentication (DefaultAzureCredential or service principal). Run with:
 *
 * <pre>
 * mvn verify -Dcamel.component.azure-functions.function-app=myFunctionApp \
 *            -Dcamel.component.azure-functions.subscription-id=mySubscriptionId \
 *            -Dcamel.component.azure-functions.resource-group=myResourceGroup
 * </pre>
 *
 * For service principal authentication, also set:
 *
 * <pre>
 * -DAZURE_CLIENT_ID=xxx -DAZURE_CLIENT_SECRET=xxx -DAZURE_TENANT_ID=xxx
 * </pre>
 */
@EnabledIfSystemProperty(named = BaseFunctionsTestSupport.SUBSCRIPTION_ID_PROPERTY, matches = ".*",
                         disabledReason = "Azure subscription credentials must be supplied to run this test, e.g: "
                                          + "mvn verify -D" + BaseFunctionsTestSupport.FUNCTION_APP_PROPERTY + "=myApp "
                                          + "-D" + BaseFunctionsTestSupport.SUBSCRIPTION_ID_PROPERTY + "=mySubscription "
                                          + "-D" + BaseFunctionsTestSupport.RESOURCE_GROUP_PROPERTY + "=myResourceGroup")
public class FunctionsProducerManagementIT extends BaseFunctionsTestSupport {

    private static final String DIRECT_LIST_APPS = "direct:listApps";
    private static final String DIRECT_GET_APP = "direct:getApp";
    private static final String DIRECT_LIST_FUNCTIONS = "direct:listFunctions";
    private static final String DIRECT_GET_CONFIG = "direct:getConfig";
    private static final String DIRECT_LIST_TAGS = "direct:listTags";

    private ProducerTemplate producerTemplate;

    @BeforeEach
    void beforeEach() {
        producerTemplate = contextExtension.getProducerTemplate();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // List function apps in resource group
                from(DIRECT_LIST_APPS)
                        .toF("azure-functions:%s?operation=listFunctionApps", FUNCTION_APP);

                // Get function app details
                from(DIRECT_GET_APP)
                        .toF("azure-functions:%s?operation=getFunctionApp", FUNCTION_APP);

                // List functions in app
                from(DIRECT_LIST_FUNCTIONS)
                        .toF("azure-functions:%s?operation=listFunctions", FUNCTION_APP);

                // Get function app configuration
                from(DIRECT_GET_CONFIG)
                        .toF("azure-functions:%s?operation=getFunctionAppConfiguration", FUNCTION_APP);

                // List tags
                from(DIRECT_LIST_TAGS)
                        .toF("azure-functions:%s?operation=listTags", FUNCTION_APP);
            }
        };
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListFunctionApps() {
        Exchange result = producerTemplate.request(DIRECT_LIST_APPS, exchange -> {
            // No body needed
        });

        assertNotNull(result);
        assertNotNull(result.getMessage().getBody());

        List<Map<String, Object>> apps = result.getMessage().getBody(List.class);
        assertNotNull(apps, "Should return list of function apps");
        // The list might be empty, but should not be null
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetFunctionApp() {
        Exchange result = producerTemplate.request(DIRECT_GET_APP, exchange -> {
            // No body needed
        });

        assertNotNull(result);

        Map<String, Object> app = result.getMessage().getBody(Map.class);
        assertNotNull(app, "Should return function app details");
        assertTrue(app.containsKey("name"), "Should contain name");
        assertTrue(app.containsKey("id"), "Should contain id");
        assertTrue(app.containsKey("state"), "Should contain state");

        // Check headers
        assertNotNull(result.getMessage().getHeader(FunctionsConstants.RESOURCE_ID));
        assertNotNull(result.getMessage().getHeader(FunctionsConstants.DEFAULT_HOSTNAME));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListFunctions() {
        Exchange result = producerTemplate.request(DIRECT_LIST_FUNCTIONS, exchange -> {
            // No body needed
        });

        assertNotNull(result);

        List<Map<String, Object>> functions = result.getMessage().getBody(List.class);
        assertNotNull(functions, "Should return list of functions");
        // Might be empty if no functions deployed
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetFunctionAppConfiguration() {
        Exchange result = producerTemplate.request(DIRECT_GET_CONFIG, exchange -> {
            // No body needed
        });

        assertNotNull(result);

        Map<String, String> config = result.getMessage().getBody(Map.class);
        assertNotNull(config, "Should return configuration map");
        // Should at least have some settings
        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListTags() {
        Exchange result = producerTemplate.request(DIRECT_LIST_TAGS, exchange -> {
            // No body needed
        });

        assertNotNull(result);

        Map<String, String> tags = result.getMessage().getBody(Map.class);
        assertNotNull(tags, "Should return tags map (may be empty)");
    }
}
