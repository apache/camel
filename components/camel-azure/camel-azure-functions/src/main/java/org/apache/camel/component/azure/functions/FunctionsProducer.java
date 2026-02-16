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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.resourcemanager.appservice.AppServiceManager;
import com.azure.resourcemanager.appservice.models.AppSetting;
import com.azure.resourcemanager.appservice.models.FunctionApp;
import com.azure.resourcemanager.appservice.models.FunctionAppBasic;
import com.azure.resourcemanager.appservice.models.FunctionEnvelope;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.azure.functions.client.FunctionsClientFactory;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient.FunctionInvocationResponse;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for Azure Functions operations.
 */
public class FunctionsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(FunctionsProducer.class);

    private AppServiceManager appServiceManager;
    private FunctionsInvocationClient invocationClient;
    private FunctionsConfigurationOptionsProxy configProxy;

    public FunctionsProducer(FunctionsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        configProxy = new FunctionsConfigurationOptionsProxy(getConfiguration());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        FunctionsClientFactory clientFactory = getEndpoint().getClientFactory();

        // Initialize invocation client
        invocationClient = clientFactory.createInvocationClient(getConfiguration());

        // Initialize management client only if needed for management operations
        if (requiresManagementClient()) {
            appServiceManager = clientFactory.createAppServiceManager(getConfiguration());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // AppServiceManager doesn't need explicit cleanup
        super.doStop();
    }

    private boolean requiresManagementClient() {
        FunctionsOperations operation = getConfiguration().getOperation();
        return operation != FunctionsOperations.invokeFunction;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        FunctionsOperations operation = configProxy.getOperation(exchange);

        if (operation == null) {
            operation = FunctionsOperations.invokeFunction;
        }

        LOG.debug("Executing operation: {}", operation);

        switch (operation) {
            case invokeFunction:
                invokeFunction(exchange);
                break;
            case listFunctionApps:
                listFunctionApps(exchange);
                break;
            case getFunctionApp:
                getFunctionApp(exchange);
                break;
            case createFunctionApp:
                createFunctionApp(exchange);
                break;
            case deleteFunctionApp:
                deleteFunctionApp(exchange);
                break;
            case startFunctionApp:
                startFunctionApp(exchange);
                break;
            case stopFunctionApp:
                stopFunctionApp(exchange);
                break;
            case restartFunctionApp:
                restartFunctionApp(exchange);
                break;
            case listFunctions:
                listFunctions(exchange);
                break;
            case getFunction:
                getFunction(exchange);
                break;
            case getFunctionKeys:
                getFunctionKeys(exchange);
                break;
            case getFunctionAppConfiguration:
                getFunctionAppConfiguration(exchange);
                break;
            case updateFunctionAppConfiguration:
                updateFunctionAppConfiguration(exchange);
                break;
            case listTags:
                listTags(exchange);
                break;
            case tagResource:
                tagResource(exchange);
                break;
            case untagResource:
                untagResource(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private void invokeFunction(Exchange exchange) throws Exception {
        String functionApp = configProxy.getFunctionApp(exchange);
        String functionName = configProxy.getFunctionName(exchange);
        String httpMethod = configProxy.getHttpMethod(exchange);
        Map<String, String> headers = configProxy.getHttpHeaders(exchange);

        if (ObjectHelper.isEmpty(functionName)) {
            throw new IllegalArgumentException("Function name is required for invokeFunction operation");
        }

        String url = String.format("https://%s.azurewebsites.net/api/%s", functionApp, functionName);

        Object body = exchange.getIn().getBody();

        LOG.debug("Invoking function: {} with method: {}", url, httpMethod);

        FunctionInvocationResponse response = invocationClient.invoke(url, httpMethod, body, headers);

        Message message = exchange.getMessage();
        message.setBody(response.getBody());
        message.setHeader(FunctionsConstants.STATUS_CODE, response.getStatusCode());
        message.setHeader(FunctionsConstants.RESPONSE_HEADERS, response.getHeaders());
    }

    private void listFunctionApps(Exchange exchange) {
        ensureManagementClient();

        String resourceGroup = configProxy.getResourceGroup(exchange);

        List<Map<String, Object>> result = new ArrayList<>();

        if (ObjectHelper.isNotEmpty(resourceGroup)) {
            for (FunctionAppBasic app : appServiceManager.functionApps().listByResourceGroup(resourceGroup)) {
                result.add(functionAppBasicToMap(app));
            }
        } else {
            for (FunctionAppBasic app : appServiceManager.functionApps().list()) {
                result.add(functionAppBasicToMap(app));
            }
        }

        exchange.getMessage().setBody(result);
    }

    private void getFunctionApp(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        Message message = exchange.getMessage();
        message.setBody(functionAppToMap(app));
        message.setHeader(FunctionsConstants.FUNCTION_APP_STATE, app.state());
        message.setHeader(FunctionsConstants.RESOURCE_ID, app.id());
        message.setHeader(FunctionsConstants.DEFAULT_HOSTNAME, app.defaultHostname());
    }

    private void createFunctionApp(Exchange exchange) {
        ensureManagementClient();

        String functionAppName = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);
        String location = configProxy.getLocation(exchange);
        String runtime = configProxy.getRuntime(exchange);
        String storageConnectionString = configProxy.getStorageConnectionString(exchange);

        if (ObjectHelper.isEmpty(location)) {
            throw new IllegalArgumentException("Location is required for createFunctionApp operation");
        }
        if (ObjectHelper.isEmpty(storageConnectionString)) {
            throw new IllegalArgumentException("Storage connection string is required for createFunctionApp operation");
        }

        // Build the function app - create base then update with settings
        FunctionApp app = appServiceManager.functionApps()
                .define(functionAppName)
                .withRegion(location)
                .withExistingResourceGroup(resourceGroup)
                .withNewConsumptionPlan()
                .withRuntimeVersion("~4")
                .create();

        // Update with app settings after creation
        if ("java".equalsIgnoreCase(runtime)) {
            app.update()
                    .withAppSetting("AzureWebJobsStorage", storageConnectionString)
                    .withAppSetting("FUNCTIONS_WORKER_RUNTIME", "java")
                    .apply();
        } else if ("node".equalsIgnoreCase(runtime)) {
            app.update()
                    .withAppSetting("AzureWebJobsStorage", storageConnectionString)
                    .withAppSetting("FUNCTIONS_WORKER_RUNTIME", "node")
                    .apply();
        } else if ("python".equalsIgnoreCase(runtime)) {
            app.update()
                    .withAppSetting("AzureWebJobsStorage", storageConnectionString)
                    .withAppSetting("FUNCTIONS_WORKER_RUNTIME", "python")
                    .apply();
        } else if ("dotnet".equalsIgnoreCase(runtime)) {
            app.update()
                    .withAppSetting("AzureWebJobsStorage", storageConnectionString)
                    .withAppSetting("FUNCTIONS_WORKER_RUNTIME", "dotnet")
                    .apply();
        } else {
            // Default
            app.update()
                    .withAppSetting("AzureWebJobsStorage", storageConnectionString)
                    .apply();
        }

        // Refresh the app to get updated state
        app = appServiceManager.functionApps().getByResourceGroup(resourceGroup, functionAppName);

        Message message = exchange.getMessage();
        message.setBody(functionAppToMap(app));
        message.setHeader(FunctionsConstants.RESOURCE_ID, app.id());
        message.setHeader(FunctionsConstants.DEFAULT_HOSTNAME, app.defaultHostname());
    }

    private void deleteFunctionApp(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        appServiceManager.functionApps()
                .deleteByResourceGroup(resourceGroup, functionApp);

        exchange.getMessage().setBody(true);
    }

    private void startFunctionApp(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        app.start();

        Message message = exchange.getMessage();
        message.setBody(true);
        message.setHeader(FunctionsConstants.FUNCTION_APP_STATE, "Running");
    }

    private void stopFunctionApp(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        app.stop();

        Message message = exchange.getMessage();
        message.setBody(true);
        message.setHeader(FunctionsConstants.FUNCTION_APP_STATE, "Stopped");
    }

    private void restartFunctionApp(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        app.restart();

        Message message = exchange.getMessage();
        message.setBody(true);
        message.setHeader(FunctionsConstants.FUNCTION_APP_STATE, "Running");
    }

    private void listFunctions(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (FunctionEnvelope function : app.listFunctions()) {
            result.add(functionEnvelopeToMap(function));
        }

        exchange.getMessage().setBody(result);
    }

    private void getFunction(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String functionName = configProxy.getFunctionName(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        if (ObjectHelper.isEmpty(functionName)) {
            throw new IllegalArgumentException("Function name is required for getFunction operation");
        }

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        FunctionEnvelope function = null;
        for (FunctionEnvelope f : app.listFunctions()) {
            String fName = f.innerModel() != null ? f.innerModel().name() : null;
            if (fName != null && fName.endsWith("/" + functionName)) {
                function = f;
                break;
            }
        }

        if (function == null) {
            throw new IllegalArgumentException(
                    String.format("Function '%s' not found in app '%s'", functionName, functionApp));
        }

        exchange.getMessage().setBody(functionEnvelopeToMap(function));
    }

    private void getFunctionKeys(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        Map<String, Object> keys = new HashMap<>();
        keys.put("masterKey", app.getMasterKey());

        exchange.getMessage().setBody(keys);
    }

    private void getFunctionAppConfiguration(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        // Convert Map<String, AppSetting> to Map<String, String>
        Map<String, AppSetting> appSettings = app.getAppSettings();
        Map<String, String> settings = appSettings.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));

        exchange.getMessage().setBody(settings);
    }

    private void updateFunctionAppConfiguration(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);
        Map<String, String> appSettings = configProxy.getAppSettings(exchange);

        if (appSettings == null || appSettings.isEmpty()) {
            throw new IllegalArgumentException(
                    "App settings are required for updateFunctionAppConfiguration operation. "
                                               + "Set the " + FunctionsConstants.APP_SETTINGS + " header.");
        }

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        // Apply settings one by one
        for (Map.Entry<String, String> entry : appSettings.entrySet()) {
            app.update()
                    .withAppSetting(entry.getKey(), entry.getValue())
                    .apply();
        }

        // Refresh and return updated settings
        app = appServiceManager.functionApps().getByResourceGroup(resourceGroup, functionApp);
        Map<String, AppSetting> updatedSettings = app.getAppSettings();
        Map<String, String> settings = updatedSettings.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));

        exchange.getMessage().setBody(settings);
    }

    private void listTags(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        exchange.getMessage().setBody(app.tags());
    }

    private void tagResource(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);
        Map<String, String> tags = configProxy.getResourceTags(exchange);

        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tags are required for tagResource operation. "
                                               + "Set the " + FunctionsConstants.RESOURCE_TAGS + " header.");
        }

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        // Apply tags one by one
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            app.update()
                    .withTag(entry.getKey(), entry.getValue())
                    .apply();
        }

        // Refresh
        app = appServiceManager.functionApps().getByResourceGroup(resourceGroup, functionApp);
        exchange.getMessage().setBody(app.tags());
    }

    private void untagResource(Exchange exchange) {
        ensureManagementClient();

        String functionApp = configProxy.getFunctionApp(exchange);
        String resourceGroup = configProxy.getResourceGroup(exchange);
        List<String> tagKeys = configProxy.getTagKeys(exchange);

        if (tagKeys == null || tagKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tag keys are required for untagResource operation. "
                                               + "Set the " + FunctionsConstants.TAG_KEYS + " header.");
        }

        FunctionApp app = appServiceManager.functionApps()
                .getByResourceGroup(resourceGroup, functionApp);

        if (app == null) {
            throw new IllegalArgumentException(
                    String.format("Function app '%s' not found in resource group '%s'", functionApp, resourceGroup));
        }

        // Remove tags one by one
        for (String key : tagKeys) {
            app.update()
                    .withoutTag(key)
                    .apply();
        }

        // Refresh
        app = appServiceManager.functionApps().getByResourceGroup(resourceGroup, functionApp);
        exchange.getMessage().setBody(app.tags());
    }

    private void ensureManagementClient() {
        if (appServiceManager == null) {
            appServiceManager = getEndpoint().getClientFactory().createAppServiceManager(getConfiguration());
        }
    }

    private Map<String, Object> functionAppBasicToMap(FunctionAppBasic app) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", app.name());
        map.put("id", app.id());
        map.put("resourceGroupName", app.resourceGroupName());
        map.put("regionName", app.regionName());
        map.put("state", app.state());
        map.put("defaultHostname", app.defaultHostname());
        return map;
    }

    private Map<String, Object> functionAppToMap(FunctionApp app) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", app.name());
        map.put("id", app.id());
        map.put("resourceGroupName", app.resourceGroupName());
        map.put("regionName", app.regionName());
        map.put("state", app.state());
        map.put("defaultHostname", app.defaultHostname());
        map.put("enabled", app.enabled());
        map.put("operatingSystem", app.operatingSystem());
        map.put("tags", app.tags());
        return map;
    }

    private Map<String, Object> functionEnvelopeToMap(FunctionEnvelope function) {
        Map<String, Object> functionMap = new HashMap<>();
        if (function.innerModel() != null) {
            functionMap.put("name", function.innerModel().name());
            functionMap.put("id", function.innerModel().id());
            functionMap.put("type", function.innerModel().type());
            functionMap.put("scriptHref", function.innerModel().scriptHref());
            functionMap.put("configHref", function.innerModel().configHref());
            functionMap.put("isDisabled", function.innerModel().isDisabled());
            functionMap.put("invokeUrlTemplate", function.innerModel().invokeUrlTemplate());
        }
        return functionMap;
    }

    @Override
    public FunctionsEndpoint getEndpoint() {
        return (FunctionsEndpoint) super.getEndpoint();
    }

    private FunctionsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
