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
package org.apache.camel.component.aws2.ddb.localstack;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ddb.Ddb2Constants;
import org.apache.camel.component.aws2.ddb.Ddb2Operations;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AWS2GetItemRuleIT extends Aws2DDBBase {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    private final String attributeName = "clave";
    private final String tableName = "TestTable";

    private final String randomId = String.valueOf(System.currentTimeMillis());

    @Test
    public void putItem() {
        final Map<String, AttributeValue> attributeMap = new HashMap<>();
        AttributeValue attributeValue = AttributeValue.builder().s("hello").build();
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

        HashMap<String, AttributeValue> itemKey = new HashMap<String, AttributeValue>();

        itemKey.put(attributeName, AttributeValue.builder().s("hello").build());

        exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.GetItem);
                exchange.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);
                exchange.getIn().setHeader(Ddb2Constants.KEY, itemKey);
                exchange.getIn().setHeader(Ddb2Constants.ATTRIBUTE_NAMES, attributeMap.keySet());
            }
        });

        assertNotNull(exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTES));
        Map<String, AttributeValue> attributes = exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTES, Map.class);
        assertEquals("value", attributes.get("secondary_attribute").s());
        assertEquals("hello", attributes.get("clave").s());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(
                        "aws2-ddb://" + tableName + "?keyAttributeName=" + attributeName + "&keyAttributeType=" + KeyType.HASH
                                        + "&keyScalarType=" + ScalarAttributeType.S
                                        + "&readCapacity=1&writeCapacity=1");
            }
        };
    }
}
