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
package org.apache.camel.component.aws.ddb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BatchGetItemsCommandTest {

    private BatchGetItemsCommand command;
    private AmazonDDBClientMock ddbClient;
    private DdbConfiguration configuration;
    private Exchange exchange;

    @Before
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new DdbConfiguration();
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new BatchGetItemsCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("1", new AttributeValue("Key_1"));
        Map<String, AttributeValue> unprocessedKey = new HashMap<>();
        unprocessedKey.put("1", new AttributeValue("UNPROCESSED_KEY"));
        Map<String, KeysAndAttributes> keysAndAttributesMap = new HashMap<>();
        KeysAndAttributes keysAndAttributes = new KeysAndAttributes().withKeys(key);
        keysAndAttributesMap.put("DOMAIN1", keysAndAttributes);
        exchange.getIn().setHeader(DdbConstants.BATCH_ITEMS, keysAndAttributesMap);

        command.execute();

        assertEquals(keysAndAttributesMap, ddbClient.batchGetItemRequest.getRequestItems());


        List<Map<String, AttributeValue>> batchResponse = (List<Map<String, AttributeValue>>)exchange.getIn().getHeader(DdbConstants.BATCH_RESPONSE, Map.class).get("DOMAIN1");
        AttributeValue value = batchResponse.get(0).get("attrName");

        KeysAndAttributes unProcessedAttributes = (KeysAndAttributes)exchange.getIn().getHeader(
                DdbConstants.UNPROCESSED_KEYS, Map.class).get("DOMAIN1");
        Map<String, AttributeValue> next = unProcessedAttributes.getKeys().iterator().next();

        assertEquals(new AttributeValue("attrValue"), value);
        assertEquals(unprocessedKey, next);
    }
}
