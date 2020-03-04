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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetItemCommandTest {
    private GetItemCommand command;
    private AmazonDDBClientMock ddbClient;
    private Ddb2Configuration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new Ddb2Configuration();
        configuration.setTableName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new GetItemCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("1", AttributeValue.builder().s("Key_1").build());
        exchange.getIn().setHeader(Ddb2Constants.KEY, key);

        List<String> attrNames = Arrays.asList("attrName");
        exchange.getIn().setHeader(Ddb2Constants.ATTRIBUTE_NAMES, attrNames);
        exchange.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);

        command.execute();

        assertEquals("DOMAIN1", ddbClient.getItemRequest.tableName());
        assertEquals(attrNames, ddbClient.getItemRequest.attributesToGet());
        assertEquals(true, ddbClient.getItemRequest.consistentRead());
        assertEquals(key, ddbClient.getItemRequest.key());
        assertEquals(AttributeValue.builder().s("attrValue").build(), exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTES, Map.class).get("attrName"));
    }
}
