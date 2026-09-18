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
package org.apache.camel.component.alibaba.sls;

import java.util.Map;

import com.aliyun.sls20201230.Client;
import com.aliyun.sls20201230.models.GetLogsRequest;
import com.aliyun.sls20201230.models.GetLogsResponse;
import com.aliyun.sls20201230.models.ListLogStoresRequest;
import com.aliyun.sls20201230.models.ListLogStoresResponse;
import com.aliyun.sls20201230.models.PutLogsRequest;
import com.aliyun.sls20201230.models.PutLogsResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.sls.constants.AlibabaSlsHeaders;
import org.apache.camel.component.alibaba.sls.constants.AlibabaSlsOperations;
import org.apache.camel.component.alibaba.sls.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class AlibabaSlsProducer extends DefaultProducer {

    public AlibabaSlsProducer(AlibabaSlsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AlibabaSlsEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = AlibabaSlsUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.getOperation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        Client slsClient = endpoint.initClient();

        switch (configuration.getOperation()) {
            case AlibabaSlsOperations.PUT_LOGS -> putLogs(exchange, configuration, slsClient);
            case AlibabaSlsOperations.GET_LOGS -> getLogs(exchange, configuration, slsClient);
            case AlibabaSlsOperations.LIST_LOG_STORES -> listLogStores(exchange, configuration, slsClient);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.getOperation());
        }
    }

    private void putLogs(Exchange exchange, ClientConfigurations configuration, Client slsClient) throws Exception {
        validateProjectAndLogStore(configuration);

        PutLogsRequest request = AlibabaSlsUtils.resolvePutLogsRequest(exchange);
        PutLogsResponse response = slsClient.putLogs(
                configuration.getProject(),
                configuration.getLogStoreName(),
                request);

        exchange.getMessage().setBody(AlibabaSlsUtils.toPutLogsMap(response));
        setResponseHeaders(exchange, response.getStatusCode(), response.getHeaders());
    }

    private void getLogs(Exchange exchange, ClientConfigurations configuration, Client slsClient) throws Exception {
        validateProjectAndLogStore(configuration);

        GetLogsRequest request = AlibabaSlsUtils.buildGetLogsRequest(configuration);
        GetLogsResponse response = slsClient.getLogs(
                configuration.getProject(),
                configuration.getLogStoreName(),
                request);

        exchange.getMessage().setBody(AlibabaSlsUtils.toGetLogsMap(response));
        setResponseHeaders(exchange, response.getStatusCode(), response.getHeaders());
    }

    private void listLogStores(Exchange exchange, ClientConfigurations configuration, Client slsClient) throws Exception {
        validateProject(configuration);

        ListLogStoresRequest request = AlibabaSlsUtils.buildListLogStoresRequest(configuration);
        ListLogStoresResponse response = slsClient.listLogStores(configuration.getProject(), request);

        exchange.getMessage().setBody(AlibabaSlsUtils.toListLogStoresMap(response));
        setResponseHeaders(exchange, response.getStatusCode(), response.getHeaders());
    }

    private void validateProjectAndLogStore(ClientConfigurations configuration) {
        if (ObjectHelper.isEmpty(configuration.getProject()) || ObjectHelper.isEmpty(configuration.getLogStoreName())) {
            throw new IllegalArgumentException("Project and log store name are required");
        }
    }

    private void validateProject(ClientConfigurations configuration) {
        if (ObjectHelper.isEmpty(configuration.getProject())) {
            throw new IllegalArgumentException("Project is required");
        }
    }

    private void setResponseHeaders(Exchange exchange, Integer statusCode, Map<String, String> headers) {
        if (statusCode != null) {
            exchange.getMessage().setHeader(AlibabaSlsHeaders.STATUS_CODE, statusCode);
        }
        String requestId = AlibabaSlsUtils.extractRequestId(headers);
        if (ObjectHelper.isNotEmpty(requestId)) {
            exchange.getMessage().setHeader(AlibabaSlsHeaders.REQUEST_ID, requestId);
        }
    }

    @Override
    public AlibabaSlsEndpoint getEndpoint() {
        return (AlibabaSlsEndpoint) super.getEndpoint();
    }
}
