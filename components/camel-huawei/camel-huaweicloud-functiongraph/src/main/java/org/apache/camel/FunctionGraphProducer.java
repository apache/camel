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
package org.apache.camel;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.sdk.functiongraph.v2.FunctionGraphClient;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionRequest;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionResponse;
import org.apache.camel.constants.FunctionGraphConstants;
import org.apache.camel.constants.FunctionGraphOperations;
import org.apache.camel.constants.FunctionGraphProperties;
import org.apache.camel.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FunctionGraphProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(FunctionGraphProducer.class);
    private FunctionGraphEndpoint endpoint;
    private FunctionGraphClient functionGraphClient;

    public FunctionGraphProducer(FunctionGraphEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        ClientConfigurations clientConfigurations = new ClientConfigurations(endpoint);

        if (functionGraphClient == null) {
            LOG.debug("Initializing SDK client");
            this.functionGraphClient = endpoint.initClient();
            LOG.debug("Successfully initialized SDK client");
        }

        updateClientConfigs(exchange, clientConfigurations);

        switch (clientConfigurations.getOperation()) {
            case FunctionGraphOperations.INVOKE_FUNCTION:
                invokeFunction(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    /**
     * Perform invoke function operation and map return object to exchange body
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void invokeFunction(Exchange exchange, ClientConfigurations clientConfigurations) {

        // convert exchange body to Map object
        Object body = exchange.getMessage().getBody();
        Map request;
        if (body instanceof Map) {
            request = exchange.getMessage().getBody(Map.class);
        } else if (body instanceof String) {
            String strBody = exchange.getMessage().getBody(String.class);
            try {
                request = new ObjectMapper().readValue(strBody, HashMap.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Request body must be a JSON or a HashMap");
            }
        } else {
            throw new IllegalArgumentException("Exchange body is mandatory and should be a valid Map or JSON string");
        }

        // checking for function name and function package
        if (ObjectHelper.isEmpty(clientConfigurations.getFunctionName())) {
            throw new IllegalArgumentException("Function name is mandatory for invokeFunction.");
        }

        // invoke the function
        InvokeFunctionRequest invokeFunctionRequest = new InvokeFunctionRequest()
                .withBody(request)
                .withFunctionUrn(FunctionGraphUtils.composeUrn(FunctionGraphConstants.URN_FORMAT, clientConfigurations))
                .withXCFFRequestVersion(FunctionGraphConstants.REQUEST_VERSION);

        if (ObjectHelper.isNotEmpty(clientConfigurations.getXCffLogType())) {
            invokeFunctionRequest.withXCffLogType(clientConfigurations.getXCffLogType());
        }

        InvokeFunctionResponse response = functionGraphClient.invokeFunction(invokeFunctionRequest);
        String responseBody
                = FunctionGraphUtils.extractJsonFieldAsString(response.getResult(), FunctionGraphConstants.RESPONSE_BODY);
        exchange.getMessage().setBody(responseBody);
        if (ObjectHelper.isNotEmpty(clientConfigurations.getXCffLogType())) {
            exchange.setProperty(FunctionGraphProperties.XCFFLOGS, response.getLog());
        }

        LOG.debug("Invoke Function results: {}", response);
    }

    /**
     * Update dynamic client configurations. Some endpoint parameters (operation, function name, package, and
     * XCFFLogType) can also be passed via exchange properties, so they can be updated between each transaction. Since
     * they can change, we must clear the previous transaction and update these parameters with their new values
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void updateClientConfigs(Exchange exchange, ClientConfigurations clientConfigurations) {

        // checking for required operation
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.OPERATION))
                && ObjectHelper.isEmpty(endpoint.getOperation())) {
            throw new IllegalArgumentException("Operation name not found");
        } else {
            clientConfigurations.setOperation(
                    ObjectHelper.isNotEmpty(exchange.getProperty(FunctionGraphProperties.OPERATION))
                            ? (String) exchange.getProperty(FunctionGraphProperties.OPERATION)
                            : endpoint.getOperation());
        }

        // checking for required function name (exchange overrides endpoint function name)
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.FUNCTION_NAME))
                && ObjectHelper.isEmpty(endpoint.getFunctionName())) {
            throw new IllegalArgumentException("Function name not found");
        } else {
            clientConfigurations.setFunctionName(
                    ObjectHelper.isNotEmpty(exchange.getProperty(FunctionGraphProperties.FUNCTION_NAME))
                            ? (String) exchange.getProperty(FunctionGraphProperties.FUNCTION_NAME)
                            : endpoint.getFunctionName());
        }

        // checking for optional function package (exchange overrides endpoint function package)
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.FUNCTION_PACKAGE))
                && ObjectHelper.isEmpty(endpoint.getFunctionPackage())) {
            clientConfigurations.setFunctionPackage(FunctionGraphConstants.DEFAULT_FUNCTION_PACKAGE);
        } else {
            clientConfigurations.setFunctionPackage(
                    ObjectHelper.isNotEmpty(exchange.getProperty(FunctionGraphProperties.FUNCTION_PACKAGE))
                            ? (String) exchange.getProperty(FunctionGraphProperties.FUNCTION_PACKAGE)
                            : endpoint.getFunctionPackage());
        }

        // checking for optional XCffLogType
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.XCFFLOGTYPE))) {
            LOG.warn("No XCffLogType given");
        } else {
            clientConfigurations.setXCffLogType((String) exchange.getProperty(FunctionGraphProperties.XCFFLOGTYPE));
        }
    }
}
