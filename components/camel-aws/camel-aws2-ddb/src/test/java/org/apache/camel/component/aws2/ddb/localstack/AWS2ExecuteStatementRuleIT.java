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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AWS2ExecuteStatementRuleIT extends Aws2DDBBase {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    private final String attributeName = "clave";
    private final String tableName = "TestTablePartiQL";

    @Test
    public void executeStatement() {
        // First put an item
        final Map<String, AttributeValue> attributeMap = new HashMap<>();
        attributeMap.put(attributeName, AttributeValue.builder().s("hello").build());
        attributeMap.put("data", AttributeValue.builder().s("testValue").build());

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.PutItem);
                exchange.getIn().setHeader(Ddb2Constants.ITEM, attributeMap);
            }
        });

        // Now query using PartiQL
        Exchange result = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.ExecuteStatement);
                exchange.getIn().setHeader(Ddb2Constants.STATEMENT,
                        "SELECT * FROM \"" + tableName + "\" WHERE \"" + attributeName + "\" = ?");
                exchange.getIn().setHeader(Ddb2Constants.STATEMENT_PARAMETERS,
                        Arrays.asList(AttributeValue.builder().s("hello").build()));
            }
        });

        @SuppressWarnings("unchecked")
        List<Map<String, AttributeValue>> items
                = result.getIn().getHeader(Ddb2Constants.EXECUTE_STATEMENT_ITEMS, List.class);
        assertNotNull(items);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(
                        "aws2-ddb://" + tableName + "?keyAttributeName=" + attributeName + "&keyAttributeType=" + KeyType.HASH
                                        + "&keyScalarType=" + ScalarAttributeType.S
                                        + "&readCapacity=1&writeCapacity=1");
            }
        };
    }
}
