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
package org.apache.camel.component.aws2.ddb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransactWriteItemsCommandTest {

    private TransactWriteItemsCommand command;
    private AmazonDDBClientMock ddbClient;
    private Ddb2Configuration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new Ddb2Configuration();
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new TransactWriteItemsCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("key", AttributeValue.builder().s("val1").build());

        List<TransactWriteItem> transactItems = Arrays.asList(
                TransactWriteItem.builder()
                        .put(Put.builder().tableName("DOMAIN1").item(item).build())
                        .build());

        exchange.getIn().setHeader(Ddb2Constants.TRANSACT_WRITE_ITEMS, transactItems);

        command.execute();

        assertNotNull(ddbClient.transactWriteItemsRequest);
        assertEquals(transactItems, ddbClient.transactWriteItemsRequest.transactItems());
    }

    @Test
    public void executeWithClientRequestToken() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("key", AttributeValue.builder().s("val1").build());

        List<TransactWriteItem> transactItems = Arrays.asList(
                TransactWriteItem.builder()
                        .put(Put.builder().tableName("DOMAIN1").item(item).build())
                        .build());

        exchange.getIn().setHeader(Ddb2Constants.TRANSACT_WRITE_ITEMS, transactItems);
        exchange.getIn().setHeader(Ddb2Constants.TRANSACT_CLIENT_REQUEST_TOKEN, "idempotency-token-123");

        command.execute();

        assertNotNull(ddbClient.transactWriteItemsRequest);
        assertEquals("idempotency-token-123", ddbClient.transactWriteItemsRequest.clientRequestToken());
    }
}
