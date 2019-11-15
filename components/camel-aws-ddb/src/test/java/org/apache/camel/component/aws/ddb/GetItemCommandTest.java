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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GetItemCommandTest {
    private GetItemCommand command;
    private AmazonDDBClientMock ddbClient;
    private DdbConfiguration configuration;
    private Exchange exchange;

    @Before
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new DdbConfiguration();
        configuration.setTableName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new GetItemCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("1", new AttributeValue("Key_1"));
        exchange.getIn().setHeader(DdbConstants.KEY, key);

        List<String> attrNames = Arrays.asList("attrName");
        exchange.getIn().setHeader(DdbConstants.ATTRIBUTE_NAMES, attrNames);
        exchange.getIn().setHeader(DdbConstants.CONSISTENT_READ, true);

        command.execute();

        assertEquals("DOMAIN1", ddbClient.getItemRequest.getTableName());
        assertEquals(attrNames, ddbClient.getItemRequest.getAttributesToGet());
        assertEquals(true, ddbClient.getItemRequest.getConsistentRead());
        assertEquals(key, ddbClient.getItemRequest.getKey());
        assertEquals(new AttributeValue("attrValue"),
                exchange.getIn().getHeader(DdbConstants.ATTRIBUTES, Map.class).get(
                        "attrName"));
    }
}
