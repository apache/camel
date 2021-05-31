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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.functiongraph.v2.FunctionGraphClient;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionRequest;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionResponse;
import com.huaweicloud.sdk.functiongraph.v2.region.FunctionGraphRegion;
import org.apache.camel.constants.FunctionGraphConstants;
import org.apache.camel.constants.FunctionGraphOperations;
import org.apache.camel.constants.FunctionGraphProperties;
import org.apache.camel.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class FunctionGraphProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(FunctionGraphProducer.class);
    private FunctionGraphEndpoint endpoint;
    private ClientConfigurations clientConfigurations;
    private FunctionGraphClient functionGraphClient;
    private BasicCredentials auth;
    private HttpConfig httpConfig;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.clientConfigurations = new ClientConfigurations(this.endpoint);
        initClient();
    }

    public FunctionGraphProducer(FunctionGraphEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        updateClientConfigs(exchange);

        switch (clientConfigurations.getOperation()) {
            case FunctionGraphOperations.INVOKE_FUNCTION:
                invokeFunction(exchange);
                break;
            default:
                throw new UnsupportedOperationException(String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    private void invokeFunction(Exchange exchange) {

        // convert exchange body to Map object
        Object body = exchange.getMessage().getBody();
        Map request;
        if (body == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Exchange body is null");
            }
            throw new IllegalArgumentException("Exchange body is mandatory and should be a valid Map or JSON string");
        } else if (body instanceof Map) {
            request = exchange.getMessage().getBody(Map.class);
        } else {
            String strBody = exchange.getMessage().getBody(String.class);
            try {
                request = new ObjectMapper().readValue(strBody, HashMap.class);
            } catch (JsonProcessingException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Invalid response body given");
                }
                throw new IllegalArgumentException("Request body must be a JSON or a HashMap");
            }
        }

        // checking for function name and function package
        if (ObjectHelper.isEmpty(clientConfigurations.getFunctionName())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Function name not found.");
            }
            throw new IllegalArgumentException("Function name is mandatory for invokeFunction.");
        }
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.FUNCTION_PACKAGE))
                && ObjectHelper.isEmpty(endpoint.getFunctionPackage())) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Function package not found. Continuing to invoke function with 'default' function package");
            }
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
        String responseBody = FunctionGraphUtils.extractJsonFieldAsString(response.getResult(), FunctionGraphConstants.RESPONSE_BODY);
        exchange.getMessage().setBody(responseBody);
        if (ObjectHelper.isNotEmpty(clientConfigurations.getXCffLogType())) {
            exchange.setProperty(FunctionGraphProperties.XCFFLOGS, response.getLog());
        }
        LOG.info("Invoke Function results: " + response);
    }

    /**
     * Initialize the client
     */
    private void initClient() {
        if (endpoint.getFunctionGraphClient() != null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("An instance of FunctionGraphClient was set on the endpoint. Skipping creation of FunctionGraphClient" +
                        "from endpoint parameters");
            }
            functionGraphClient = endpoint.getFunctionGraphClient();
            return;
        }

        auth = new BasicCredentials()
                .withAk(clientConfigurations.getAuthenticationKey())
                .withSk(clientConfigurations.getSecretKey())
                .withProjectId(clientConfigurations.getProjectId());

        httpConfig = HttpConfig.getDefaultHttpConfig();
        httpConfig.withIgnoreSSLVerification(clientConfigurations.isIgnoreSslVerification());
        if (ObjectHelper.isNotEmpty(clientConfigurations.getProxyHost())
                && ObjectHelper.isNotEmpty(clientConfigurations.getProxyPort())) {
            httpConfig.withProxyHost(clientConfigurations.getProxyHost())
                    .withProxyPort(clientConfigurations.getProxyPort());

            if (ObjectHelper.isNotEmpty(clientConfigurations.getProxyUser())){
                httpConfig.withProxyUsername(clientConfigurations.getProxyUser());
                if (ObjectHelper.isNotEmpty(clientConfigurations.getProxyPassword())) {
                    httpConfig.withProxyPassword(clientConfigurations.getProxyPassword());
                }
            }
        }

        if (ObjectHelper.isNotEmpty(clientConfigurations.getEndpoint())) {
            functionGraphClient = FunctionGraphClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withEndpoint(clientConfigurations.getEndpoint())
                    .build();
        } else {
            functionGraphClient = FunctionGraphClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withRegion(FunctionGraphRegion.valueOf(clientConfigurations.getRegion()))
                    .build();
        }

    }

    /**
     * Update dynamic client configurations
     *
     * @param exchange
     */
    private void updateClientConfigs(Exchange exchange) {
        resetDynamicConfigs();

        // checking for required operation
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.OPERATION))
                && ObjectHelper.isEmpty(endpoint.getOperation())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No operation name given. Cannot proceed with FunctionGraph operations.");
            }
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
            if (LOG.isErrorEnabled()) {
                LOG.error("No function name given.");
            }
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
            if (LOG.isWarnEnabled()) {
                LOG.warn("No function package given. Defaulting to 'default'.");
            }
            clientConfigurations.setFunctionPackage(FunctionGraphConstants.DEFAULT_FUNCTION_PACKAGE);
        } else {
            clientConfigurations.setFunctionPackage(
                    ObjectHelper.isNotEmpty(exchange.getProperty(FunctionGraphProperties.FUNCTION_PACKAGE))
                            ? (String) exchange.getProperty(FunctionGraphProperties.FUNCTION_PACKAGE)
                            : endpoint.getFunctionPackage());
        }

        // checking for optional XCffLogType
        if (ObjectHelper.isEmpty(exchange.getProperty(FunctionGraphProperties.XCFFLOGTYPE))) {
            if (LOG.isErrorEnabled()) {
                LOG.warn("No XCffLogType given");
            }
        } else {
            clientConfigurations.setXCffLogType((String) exchange.getProperty(FunctionGraphProperties.XCFFLOGTYPE));
        }
    }

    /**
     * Set all dynamic configurations to null
     */
    private void resetDynamicConfigs() {
        clientConfigurations.setOperation(null);
        clientConfigurations.setFunctionName(null);
        clientConfigurations.setFunctionPackage(null);
        clientConfigurations.setXCffLogType(null);
    }
}
