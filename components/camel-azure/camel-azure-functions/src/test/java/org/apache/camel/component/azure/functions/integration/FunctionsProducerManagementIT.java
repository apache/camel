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

import com.azure.core.management.Region;
import com.azure.resourcemanager.appservice.AppServiceManager;
import com.azure.resourcemanager.appservice.fluent.models.SiteConfigInner;
import com.azure.resourcemanager.appservice.fluent.models.SiteInner;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.functions.FunctionsComponent;
import org.apache.camel.component.azure.functions.FunctionsConfiguration;
import org.apache.camel.component.azure.functions.FunctionsConstants;
import org.apache.camel.test.infra.azure.functions.clients.AzureFunctionsClientUtils;
import org.apache.camel.test.infra.azure.functions.services.AzureFunctionsService;
import org.apache.camel.test.infra.azure.functions.services.AzureFunctionsServiceFactory;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Azure Functions management operations.
 * <p>
 * They run against Floci AZ by default, on a function app they create there through its Azure Resource Manager
 * endpoint. Floci AZ serves the Microsoft.Web/sites resources, but not the config/logs of a site, which the Azure SDK
 * reads to get a function app: the tests of the operations on one function app run against Azure only.
 * <p>
 * To run them against Azure, with Azure AD authentication (DefaultAzureCredential or service principal):
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
public class FunctionsProducerManagementIT extends BaseFunctionsTestSupport {

    // against Floci AZ, unless a subscription is given
    private static final boolean REMOTE = SUBSCRIPTION_ID != null;

    static {
        if (REMOTE) {
            System.setProperty("azure.instance.type", "remote");
        }
    }

    @RegisterExtension
    static AzureFunctionsService service = AzureFunctionsServiceFactory.createSingletonService();

    // the function app of the tests: the given one, or one they create on Floci AZ
    private static final String APP = REMOTE ? FUNCTION_APP : "camel-functions-it";
    // the function app the delete test creates on Floci AZ
    private static final String APP_TO_DELETE = "camel-functions-it-delete";

    private static final String FLOCI_AZ_GAP
            = "Floci AZ does not serve the config/logs of a site, which the Azure SDK reads to get a function app"
              + " (GET .../sites/{name}/config/logs answers 404 ResourceNotFound)";
    private static final String REMOTE_ONLY = ". Supply an Azure subscription to run this test, e.g: mvn verify -D"
                                              + FUNCTION_APP_PROPERTY + "=myApp -D" + SUBSCRIPTION_ID_PROPERTY
                                              + "=mySubscription -D" + RESOURCE_GROUP_PROPERTY + "=myResourceGroup";

    private static final String DIRECT_LIST_APPS = "direct:listApps";
    private static final String DIRECT_GET_APP = "direct:getApp";
    private static final String DIRECT_LIST_FUNCTIONS = "direct:listFunctions";
    private static final String DIRECT_GET_CONFIG = "direct:getConfig";
    private static final String DIRECT_LIST_TAGS = "direct:listTags";
    private static final String DIRECT_DELETE_APP = "direct:deleteApp";

    private static AppServiceManager appServiceManager;

    private ProducerTemplate producerTemplate;

    @BeforeAll
    static void createFunctionApp() {
        if (!REMOTE) {
            appServiceManager = AzureFunctionsClientUtils.createLocalAppServiceManager(service);
            appServiceManager.resourceManager().resourceGroups().define(service.resourceGroup())
                    .withRegion(Region.EUROPE_WEST)
                    .create();
            createSite(APP);
        }
    }

    /**
     * Creates a function app as a Microsoft.Web/sites resource: Floci AZ serves no App Service plan, which the
     * createFunctionApp operation creates first.
     */
    private static void createSite(String name) {
        SiteInner site = new SiteInner()
                .withKind("functionapp,linux")
                .withSiteConfig(new SiteConfigInner().withLinuxFxVersion("Java|21"))
                .withLocation(Region.EUROPE_WEST.name());
        appServiceManager.serviceClient().getWebApps().createOrUpdate(service.resourceGroup(), name, site);
    }

    @BeforeEach
    void beforeEach() {
        producerTemplate = contextExtension.getProducerTemplate();
    }

    @Override
    @ContextFixture
    public void configureComponent(CamelContext context) {
        super.configureComponent(context);

        if (!REMOTE) {
            FunctionsConfiguration configuration
                    = context.getComponent("azure-functions", FunctionsComponent.class).getConfiguration();
            configuration.setSubscriptionId(service.subscriptionId());
            configuration.setResourceGroup(service.resourceGroup());
            configuration.setAppServiceManager(appServiceManager);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // List function apps in resource group
                from(DIRECT_LIST_APPS)
                        .toF("azure-functions:%s?operation=listFunctionApps", APP);

                // Get function app details
                from(DIRECT_GET_APP)
                        .toF("azure-functions:%s?operation=getFunctionApp", APP);

                // List functions in app
                from(DIRECT_LIST_FUNCTIONS)
                        .toF("azure-functions:%s?operation=listFunctions", APP);

                // Get function app configuration
                from(DIRECT_GET_CONFIG)
                        .toF("azure-functions:%s?operation=getFunctionAppConfiguration", APP);

                // List tags
                from(DIRECT_LIST_TAGS)
                        .toF("azure-functions:%s?operation=listTags", APP);

                // Delete the function app the delete test creates
                from(DIRECT_DELETE_APP)
                        .toF("azure-functions:%s?operation=deleteFunctionApp", APP_TO_DELETE);
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
    @EnabledIfSystemProperty(named = SUBSCRIPTION_ID_PROPERTY, matches = ".*",
                             disabledReason = FLOCI_AZ_GAP + ", nor the defaultHostName of a site" + REMOTE_ONLY)
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
    @EnabledIfSystemProperty(named = SUBSCRIPTION_ID_PROPERTY, matches = ".*",
                             disabledReason = FLOCI_AZ_GAP
                                              + ", nor the functions of a site (GET .../sites/{name}/functions answers 404)"
                                              + REMOTE_ONLY)
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
    @EnabledIfSystemProperty(named = SUBSCRIPTION_ID_PROPERTY, matches = ".*",
                             disabledReason = FLOCI_AZ_GAP
                                              + ", nor the app settings of a site (POST .../sites/{name}/config/appsettings/list"
                                              + " answers 404)" + REMOTE_ONLY)
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
    @EnabledIfSystemProperty(named = SUBSCRIPTION_ID_PROPERTY, matches = ".*",
                             disabledReason = FLOCI_AZ_GAP + ", and it keeps no tags of a site" + REMOTE_ONLY)
    @SuppressWarnings("unchecked")
    void testListTags() {
        Exchange result = producerTemplate.request(DIRECT_LIST_TAGS, exchange -> {
            // No body needed
        });

        assertNotNull(result);

        Map<String, String> tags = result.getMessage().getBody(Map.class);
        assertNotNull(tags, "Should return tags map (may be empty)");
    }

    @Test
    @DisabledIfSystemProperty(named = SUBSCRIPTION_ID_PROPERTY, matches = ".*",
                              disabledReason = "The test deletes a function app it creates on Floci AZ")
    void testDeleteFunctionApp() {
        createSite(APP_TO_DELETE);
        assertTrue(listFunctionAppNames().contains(APP_TO_DELETE), "Should list the function app before its deletion");

        Object deleted = producerTemplate.requestBody(DIRECT_DELETE_APP, (Object) null);

        assertEquals(Boolean.TRUE, deleted);
        List<String> names = listFunctionAppNames();
        assertFalse(names.contains(APP_TO_DELETE), "Should not list the deleted function app");
        assertTrue(names.contains(APP), "Should still list the other function app");
    }

    @SuppressWarnings("unchecked")
    private List<String> listFunctionAppNames() {
        List<Map<String, Object>> apps = producerTemplate.requestBody(DIRECT_LIST_APPS, null, List.class);
        return apps.stream().map(app -> (String) app.get("name")).toList();
    }
}
