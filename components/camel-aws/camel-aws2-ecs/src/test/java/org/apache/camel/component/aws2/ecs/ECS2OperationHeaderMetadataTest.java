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
package org.apache.camel.component.aws2.ecs;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The operation can come from the CamelAwsECSOperation header alone, and all headers are producer headers.
 */
public class ECS2OperationHeaderMetadataTest extends CamelTestSupport {

    @BindToRegistry("amazonEcsClient")
    AmazonECSClientMock clientMock = new AmazonECSClientMock();

    @Test
    public void operationOptionIsNotRequired() throws Exception {
        JsonObject properties = schema().getMap("properties");
        JsonObject operation = properties.getMap("operation");
        assertFalse(operation.getBoolean("required"), "operation must not be required, the header can replace it");
    }

    @Test
    public void operationFromHeaderOnly() {
        Exchange exchange = template.request("aws2-ecs://test?ecsClient=#amazonEcsClient",
                e -> e.getIn().setHeader(ECS2Constants.OPERATION, ECS2Operations.listClusters));

        assertInstanceOf(ListClustersResponse.class, exchange.getMessage().getBody());
    }

    @Test
    public void allHeadersAreProducerHeaders() throws Exception {
        JsonObject headers = schema().getMap("headers");
        assertEquals(6, headers.size());
        for (String name : headers.keySet()) {
            JsonObject header = headers.getMap(name);
            assertEquals("producer", header.getString("label"), name);
            assertEquals("producer", header.getString("group"), name);
        }
    }

    private JsonObject schema() throws Exception {
        String json = ((CatalogCamelContext) context).getComponentParameterJsonSchema("aws2-ecs");
        return (JsonObject) Jsoner.deserialize(json);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no routes, the tests send to the endpoint
            }
        };
    }
}
