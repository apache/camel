/**
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

import com.amazonaws.services.dynamodb.model.AttributeValue;
import com.amazonaws.services.dynamodb.model.ComparisonOperator;
import com.amazonaws.services.dynamodb.model.Condition;
import com.amazonaws.services.dynamodb.model.Key;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScanCommandTest {

    private ScanCommand command;
    private AmazonDDBClientMock ddbClient;
    private DdbConfiguration configuration;
    private Exchange exchange;

    @Before
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new DdbConfiguration();
        configuration.setTableName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new ScanCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        Map<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.GT.toString())
                .withAttributeValueList(new AttributeValue().withN("1985"));
        scanFilter.put("year", condition);
        exchange.getIn().setHeader(DdbConstants.SCAN_FILTER, scanFilter);

        command.execute();

        assertEquals(scanFilter, ddbClient.scanRequest.getScanFilter());
        assertEquals(Integer.valueOf(10), exchange.getIn().getHeader(DdbConstants.SCANNED_COUNT, Integer.class));
        assertEquals(Integer.valueOf(1), exchange.getIn().getHeader(DdbConstants.COUNT, Integer.class));
        assertEquals(Double.valueOf(1.0), exchange.getIn().getHeader(DdbConstants.CONSUMED_CAPACITY, Double.class));
        assertEquals(new Key(new AttributeValue("LAST_KEY")), exchange.getIn().getHeader(DdbConstants.LAST_EVALUATED_KEY, Key.class));

        Map<?, ?> items = (Map<?, ?>) exchange.getIn().getHeader(DdbConstants.ITEMS, List.class).get(0);
        assertEquals(new AttributeValue("attrValue"), items.get("attrName"));
    }
}