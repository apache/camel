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
package org.apache.camel.component.aws2.ddb.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ddb.Ddb2Constants;
import org.apache.camel.component.aws2.ddb.Ddb2Operations;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ExpectedAttributeValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Must be manually tested. Provide your own credentials below!")
public class DdbComponentIntegrationTest extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    // To replace with proper credentials:
    private final String attributeName = "clave";
    private final String tableName = "TestTable";
    private final String secretKey = "-";
    private final String accessKey = "-";
    private final String region = Region.EU_WEST_2.id();
    // End credentials replacement

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
        AttributeValue attributeValue = AttributeValue.builder().n(randomId).build();
        attributeMap.put(attributeName, attributeValue);
        attributeMap.put("secondary_attribute", AttributeValue.builder().s("value").build());

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.PutItem);
                exchange.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, "true");
                exchange.getIn().setHeader(Ddb2Constants.RETURN_VALUES, "ALL_OLD");
                exchange.getIn().setHeader(Ddb2Constants.ITEM, attributeMap);
                exchange.getIn().setHeader(Ddb2Constants.ATTRIBUTE_NAMES, attributeMap.keySet());
            }
        });

        assertNotNull(exchange.getIn().getHeader(Ddb2Constants.ITEM));
    }

    public void updateItem() {
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        attributeMap.put(attributeName, AttributeValue.builder().n(randomId).build());
        attributeMap.put("secondary_attribute", AttributeValue.builder().s("new").build());

        Map<String, ExpectedAttributeValue> expectedAttributeValueMap = new HashMap<>();
        expectedAttributeValueMap.put(attributeName, ExpectedAttributeValue.builder().attributeValueList(AttributeValue.builder().n(randomId).build()).build());

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Ddb2Constants.ITEM, attributeMap);
                exchange.getIn().setHeader(Ddb2Constants.UPDATE_CONDITION, expectedAttributeValueMap);
                exchange.getIn().setHeader(Ddb2Constants.ATTRIBUTE_NAMES, attributeMap.keySet());
                exchange.getIn().setHeader(Ddb2Constants.RETURN_VALUES, "ALL_OLD");
            }
        });

        assertNotNull(exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTES));
    }

    public void getItem() {

        Map<String, AttributeValue> key = new HashMap<>();
        key.put(attributeName, AttributeValue.builder().n(randomId).build());

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.GetItem);
                exchange.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);
                exchange.getIn().setHeader(Ddb2Constants.KEY, key);
                exchange.getIn().setHeader(Ddb2Constants.ATTRIBUTE_NAMES, key.keySet());
            }
        });

        assertNotNull(exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTES));
        assertEquals(AttributeValue.builder().n(randomId).build(), exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTES, Map.class).get(attributeName));
    }

    @Test
    public void deleteItem() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(attributeName, AttributeValue.builder().n(randomId).build());

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Ddb2Constants.KEY, key);
                exchange.getIn().setHeader(Ddb2Constants.RETURN_VALUES, "ALL_OLD");
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.DeleteItem);
                exchange.getIn().setHeader(Ddb2Constants.ATTRIBUTE_NAMES, key.keySet());
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("aws-ddb://" + tableName + "?" + "region=" + region + "&accessKey=" + accessKey + "&secretKey=RAW(" + secretKey + ")");
            }
        };
    }
}
