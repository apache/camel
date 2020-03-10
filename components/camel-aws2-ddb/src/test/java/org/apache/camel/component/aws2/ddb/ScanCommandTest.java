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
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.model.Condition;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScanCommandTest {

    private ScanCommand command;
    private AmazonDDBClientMock ddbClient;
    private Ddb2Configuration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new Ddb2Configuration();
        configuration.setTableName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new ScanCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        Map<String, Condition> scanFilter = new HashMap<>();
        Condition.Builder condition = Condition.builder().comparisonOperator(ComparisonOperator.GT.toString()).attributeValueList(AttributeValue.builder().n("1985").build());
        scanFilter.put("year", condition.build());
        exchange.getIn().setHeader(Ddb2Constants.SCAN_FILTER, scanFilter);

        command.execute();

        Map<String, AttributeValue> mapAssert = new HashMap<>();
        mapAssert.put("1", AttributeValue.builder().s("LAST_KEY").build());

        ConsumedCapacity consumed = (ConsumedCapacity)exchange.getIn().getHeader(Ddb2Constants.CONSUMED_CAPACITY);
        assertEquals(scanFilter, ddbClient.scanRequest.scanFilter());
        assertEquals(Integer.valueOf(10), exchange.getIn().getHeader(Ddb2Constants.SCANNED_COUNT, Integer.class));
        assertEquals(Integer.valueOf(1), exchange.getIn().getHeader(Ddb2Constants.COUNT, Integer.class));
        assertEquals(Double.valueOf(1.0), consumed.capacityUnits());
        assertEquals(mapAssert, exchange.getIn().getHeader(Ddb2Constants.LAST_EVALUATED_KEY, Map.class));

        Map<?, ?> items = (Map<?, ?>)exchange.getIn().getHeader(Ddb2Constants.ITEMS, List.class).get(0);
        assertEquals(AttributeValue.builder().s("attrValue").build(), items.get("attrName"));
    }
}
