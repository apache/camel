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
package org.apache.camel.component.aws.ddb.integration;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.ddb.DdbConstants;
import org.apache.camel.component.aws.ddb.DdbOperations;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own credentials below!")
public class DdbComponentIntegrationTest extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    //To replace with proper credentials:
    private final String attributeName = "clave";
    private final String tableName = "TestTable";
    private final String secretKey = "-";
    private final String accessKey = "-";
    private final String region = Regions.EU_WEST_2.name();
    //End credentials replacement

    private final String randomId = String.valueOf(System.currentTimeMillis());

    @Test
    public void fullLifeCycle() {
        putItem();
        getItem();
        updateItem();
        deleteItem();
    }

    public void putItem() {
        final Map<String, AttributeValue> attributeMap = new HashMap<>();
        AttributeValue attributeValue = new AttributeValue(randomId);
        attributeMap.put(attributeName, attributeValue);
        attributeMap.put("secondary_attribute", new AttributeValue("value"));

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(DdbConstants.OPERATION, DdbOperations.PutItem);
                exchange.getIn().setHeader(DdbConstants.CONSISTENT_READ, "true");
                exchange.getIn().setHeader(DdbConstants.RETURN_VALUES, "ALL_OLD");
                exchange.getIn().setHeader(DdbConstants.ITEM, attributeMap);
                exchange.getIn().setHeader(DdbConstants.ATTRIBUTE_NAMES, attributeMap.keySet());
            }
        });

        assertNotNull(exchange.getIn().getHeader(DdbConstants.ITEM));
    }


    public void updateItem() {
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        attributeMap.put(attributeName, new AttributeValue(randomId));
        attributeMap.put("secondary_attribute", new AttributeValue("new"));

        Map<String, ExpectedAttributeValue> expectedAttributeValueMap = new HashMap<>();
        expectedAttributeValueMap.put(attributeName,
                new ExpectedAttributeValue(new AttributeValue(randomId)));

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(DdbConstants.ITEM, attributeMap);
                exchange.getIn().setHeader(DdbConstants.UPDATE_CONDITION, expectedAttributeValueMap);
                exchange.getIn().setHeader(DdbConstants.ATTRIBUTE_NAMES, attributeMap.keySet());
                exchange.getIn().setHeader(DdbConstants.RETURN_VALUES, "ALL_OLD");
            }
        });

        assertNotNull(exchange.getIn().getHeader(DdbConstants.ATTRIBUTES));
    }

    public void getItem() {

        Map<String, AttributeValue> key = new HashMap<>();
        key.put(attributeName, new AttributeValue(randomId));

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(DdbConstants.OPERATION, DdbOperations.GetItem);
                exchange.getIn().setHeader(DdbConstants.CONSISTENT_READ, true);
                exchange.getIn().setHeader(DdbConstants.KEY, key);
                exchange.getIn().setHeader(DdbConstants.ATTRIBUTE_NAMES, key.keySet());
            }
        });

        assertNotNull(exchange.getIn().getHeader(DdbConstants.ATTRIBUTES));
        assertEquals(new AttributeValue(randomId),
                exchange.getIn().getHeader(DdbConstants.ATTRIBUTES, Map.class).get(
                        attributeName));
    }

    @Test
    public void deleteItem() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(attributeName, new AttributeValue(randomId));

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(DdbConstants.KEY, key);
                exchange.getIn().setHeader(DdbConstants.RETURN_VALUES, "ALL_OLD");
                exchange.getIn().setHeader(DdbConstants.OPERATION, DdbOperations.DeleteItem);
                exchange.getIn().setHeader(DdbConstants.ATTRIBUTE_NAMES, key.keySet());
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-ddb://" + tableName + "?"
                                + "region=" + region
                                + "&accessKey=" + accessKey
                                + "&secretKey=RAW(" + secretKey + ")");
            }
        };
    }
}
