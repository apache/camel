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
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AWS2BatchWriteItemRuleIT extends Aws2DDBBase {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    private final String attributeName = "clave";
    private final String tableName = "TestTableBatchWrite";

    @Test
    public void batchWriteItem() {
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put(attributeName, AttributeValue.builder().s("batchKey1").build());
        item1.put("data", AttributeValue.builder().s("batchVal1").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put(attributeName, AttributeValue.builder().s("batchKey2").build());
        item2.put("data", AttributeValue.builder().s("batchVal2").build());

        Map<String, List<WriteRequest>> requestItems = new HashMap<>();
        requestItems.put(tableName, Arrays.asList(
                WriteRequest.builder().putRequest(PutRequest.builder().item(item1).build()).build(),
                WriteRequest.builder().putRequest(PutRequest.builder().item(item2).build()).build()));

        Exchange result = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.BatchWriteItems);
                exchange.getIn().setHeader(Ddb2Constants.BATCH_WRITE_ITEMS, requestItems);
            }
        });

        assertNull(result.getException());
        assertNotNull(result.getIn().getHeader(Ddb2Constants.BATCH_WRITE_UNPROCESSED_ITEMS));
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
