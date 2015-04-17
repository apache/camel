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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class QueryCommandTest {

    private QueryCommand command;
    private AmazonDDBClientMock ddbClient;
    private DdbConfiguration configuration;
    private Exchange exchange;

    @Before
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new DdbConfiguration();
        configuration.setTableName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new QueryCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void execute() {

        Map<String, AttributeValue> startKey = new HashMap<String, AttributeValue>();
        startKey.put("1", new AttributeValue("startKey"));

        List<String> attributeNames = Arrays.asList("attrNameOne", "attrNameTwo");
        exchange.getIn().setHeader(DdbConstants.ATTRIBUTE_NAMES, attributeNames);
        exchange.getIn().setHeader(DdbConstants.CONSISTENT_READ, true);
        exchange.getIn().setHeader(DdbConstants.START_KEY, startKey);
        exchange.getIn().setHeader(DdbConstants.LIMIT, 10);
        exchange.getIn().setHeader(DdbConstants.SCAN_INDEX_FORWARD, true);
        
        Map<String, Condition> keyConditions = new HashMap<String, Condition>();
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.GT.toString())
            .withAttributeValueList(new AttributeValue().withN("1985"));
        
        keyConditions.put("1", condition);
        
        exchange.getIn().setHeader(DdbConstants.KEY_CONDITIONS, keyConditions);

        command.execute();

        Map<String, AttributeValue> mapAssert = new HashMap<String, AttributeValue>();
        mapAssert.put("1", new AttributeValue("LAST_KEY"));
        ConsumedCapacity consumed = (ConsumedCapacity) exchange.getIn().getHeader(DdbConstants.CONSUMED_CAPACITY);
        assertEquals(Integer.valueOf(1), exchange.getIn().getHeader(DdbConstants.COUNT, Integer.class));
        assertEquals(Double.valueOf(1.0), consumed.getCapacityUnits());
        assertEquals(mapAssert, exchange.getIn().getHeader(DdbConstants.LAST_EVALUATED_KEY, Map.class));
        assertEquals(keyConditions, exchange.getIn().getHeader(DdbConstants.KEY_CONDITIONS, Map.class));

        Map<?, ?> items = (Map<?, ?>) exchange.getIn().getHeader(DdbConstants.ITEMS, List.class).get(0);
        assertEquals(new AttributeValue("attrValue"), items.get("attrName"));
    }
}
