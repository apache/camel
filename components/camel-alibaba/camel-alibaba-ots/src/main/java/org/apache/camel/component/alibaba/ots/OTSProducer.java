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
import org.apache.camel.component.alibaba.ots.constants.OTSHeaders;
import org.apache.camel.component.alibaba.ots.constants.OTSOperations;
import org.apache.camel.component.alibaba.ots.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class OTSProducer extends DefaultProducer {

    private SyncClient otsClient;

    public OTSProducer(OTSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OTSEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = OTSUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.getOperation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        if (otsClient == null) {
            otsClient = endpoint.initClient();
        }

        switch (configuration.getOperation()) {
            case OTSOperations.PUT_ROW -> putRow(exchange);
            case OTSOperations.GET_ROW -> getRow(exchange);
            case OTSOperations.UPDATE_ROW -> updateRow(exchange);
            case OTSOperations.DELETE_ROW -> deleteRow(exchange);
            case OTSOperations.LIST_TABLES -> listTables(exchange);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.getOperation());
        }
    }

    private void putRow(Exchange exchange) throws Exception {
        PutRowRequest request = exchange.getMessage().getMandatoryBody(PutRowRequest.class);
        var response = otsClient.putRow(request);
        exchange.getMessage().setBody(OTSUtils.toPutRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void getRow(Exchange exchange) throws Exception {
        GetRowRequest request = exchange.getMessage().getMandatoryBody(GetRowRequest.class);
        var response = otsClient.getRow(request);
        exchange.getMessage().setBody(OTSUtils.toGetRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void updateRow(Exchange exchange) throws Exception {
        UpdateRowRequest request = exchange.getMessage().getMandatoryBody(UpdateRowRequest.class);
        var response = otsClient.updateRow(request);
        exchange.getMessage().setBody(OTSUtils.toUpdateRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void deleteRow(Exchange exchange) throws Exception {
        DeleteRowRequest request = exchange.getMessage().getMandatoryBody(DeleteRowRequest.class);
        var response = otsClient.deleteRow(request);
        exchange.getMessage().setBody(OTSUtils.toDeleteRowMap(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void listTables(Exchange exchange) throws Exception {
        var response = otsClient.listTable();
        exchange.getMessage().setBody(OTSUtils.toListTablesBody(response));
        setResponseHeaders(exchange, response.getRequestId());
    }

    private void setResponseHeaders(Exchange exchange, String requestId) {
        if (ObjectHelper.isNotEmpty(requestId)) {
            exchange.getMessage().setHeader(OTSHeaders.REQUEST_ID, requestId);
        }
    }

    @Override
    public OTSEndpoint getEndpoint() {
        return (OTSEndpoint) super.getEndpoint();
    }
}
