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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchGetItemsCommandTest {

    private BatchGetItemsCommand command;
    private AmazonDDBClientMock ddbClient;
    private Ddb2Configuration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new Ddb2Configuration();
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new BatchGetItemsCommand(ddbClient, configuration, exchange);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void execute() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("1", AttributeValue.builder().s("Key_1").build());
        Map<String, AttributeValue> unprocessedKey = new HashMap<>();
        unprocessedKey.put("1", AttributeValue.builder().s("UNPROCESSED_KEY").build());
        Map<String, KeysAndAttributes> keysAndAttributesMap = new HashMap<>();
        KeysAndAttributes keysAndAttributes = KeysAndAttributes.builder().keys(key).build();
        keysAndAttributesMap.put("DOMAIN1", keysAndAttributes);
        exchange.getIn().setHeader(Ddb2Constants.BATCH_ITEMS, keysAndAttributesMap);

        command.execute();

        assertEquals(keysAndAttributesMap, ddbClient.batchGetItemRequest.requestItems());

        List<Map<String, AttributeValue>> batchResponse = (List<Map<String, AttributeValue>>)exchange.getIn().getHeader(Ddb2Constants.BATCH_RESPONSE, Map.class).get("DOMAIN1");
        AttributeValue value = batchResponse.get(0).get("attrName");

        KeysAndAttributes unProcessedAttributes = (KeysAndAttributes)exchange.getIn().getHeader(Ddb2Constants.UNPROCESSED_KEYS, Map.class).get("DOMAIN1");
        Map<String, AttributeValue> next = unProcessedAttributes.keys().iterator().next();

        assertEquals(AttributeValue.builder().s("attrValue").build(), value);
        assertEquals(unprocessedKey, next);
    }
}
