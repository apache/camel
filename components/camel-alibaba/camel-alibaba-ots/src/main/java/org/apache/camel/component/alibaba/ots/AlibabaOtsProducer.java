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
package org.apache.camel.component.alibaba.ots;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.DeleteRowRequest;
import com.alicloud.openservices.tablestore.model.GetRowRequest;
import com.alicloud.openservices.tablestore.model.PutRowRequest;
import com.alicloud.openservices.tablestore.model.UpdateRowRequest;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.ots.constants.AlibabaOtsHeaders;
import org.apache.camel.component.alibaba.ots.constants.AlibabaOtsOperations;
import org.apache.camel.component.alibaba.ots.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class AlibabaOtsProducer extends DefaultProducer {

    public AlibabaOtsProducer(AlibabaOtsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AlibabaOtsEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = AlibabaOtsUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.getOperation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        SyncClient otsClient = endpoint.initClient();

        switch (configuration.getOperation()) {
            case AlibabaOtsOperations.PUT_ROW -> putRow(exchange, otsClient);
            case AlibabaOtsOperations.GET_ROW -> getRow(exchange, otsClient);
            case AlibabaOtsOperations.UPDATE_ROW -> updateRow(exchange, otsClient);
            case AlibabaOtsOperations.DELETE_ROW -> deleteRow(exchange, otsClient);
            case AlibabaOtsOperations.LIST_TABLES -> listTables(exchange, otsClient);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.getOperation());
        }
    }

    private void putRow(Exchange exchange, SyncClient otsClient) throws Exception {
        PutRowRequest request = exchange.getMessage().getMandatoryBody(PutRowRequest.class);
        var response = otsClient.putRow(request);
        exchange.getMessage().setBody(AlibabaOtsUtils.toPutRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void getRow(Exchange exchange, SyncClient otsClient) throws Exception {
        GetRowRequest request = exchange.getMessage().getMandatoryBody(GetRowRequest.class);
        var response = otsClient.getRow(request);
        exchange.getMessage().setBody(AlibabaOtsUtils.toGetRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void updateRow(Exchange exchange, SyncClient otsClient) throws Exception {
        UpdateRowRequest request = exchange.getMessage().getMandatoryBody(UpdateRowRequest.class);
        var response = otsClient.updateRow(request);
        exchange.getMessage().setBody(AlibabaOtsUtils.toUpdateRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void deleteRow(Exchange exchange, SyncClient otsClient) throws Exception {
        DeleteRowRequest request = exchange.getMessage().getMandatoryBody(DeleteRowRequest.class);
        var response = otsClient.deleteRow(request);
        exchange.getMessage().setBody(AlibabaOtsUtils.toDeleteRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void listTables(Exchange exchange, SyncClient otsClient) throws Exception {
        var response = otsClient.listTable();
        exchange.getMessage().setBody(AlibabaOtsUtils.toListTablesBody(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void setResponseHeaders(Exchange exchange, String requestId) {
        if (ObjectHelper.isNotEmpty(requestId)) {
            exchange.getMessage().setHeader(AlibabaOtsHeaders.REQUEST_ID, requestId);
        }
    }

    @Override
    public AlibabaOtsEndpoint getEndpoint() {
        return (AlibabaOtsEndpoint) super.getEndpoint();
    }
}
