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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the full route flow: JSON body -> transformDataType(aws2-ddb:application-json) -> aws2-ddb endpoint. This
 * mirrors the aws-ddb-sink kamelet which uses setProperty(operation) + transformDataType + to(aws2-ddb).
 */
public class Ddb2JsonDataTypeTransformerRouteTest extends CamelTestSupport {

    private AmazonDDBClientMock ddbClient;

    @Override
    protected void doPreSetup() throws Exception {
        ddbClient = new AmazonDDBClientMock();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("ddbClient", ddbClient);

                from("direct:putItem")
                        .setProperty("operation", constant(Ddb2Operations.PutItem.name()))
                        .transformDataType(null, "aws2-ddb:application-json")
                        .to("aws2-ddb://activeTable?operation=PutItem");

                from("direct:updateItem")
                        .setProperty("operation", constant(Ddb2Operations.UpdateItem.name()))
                        .transformDataType(null, "aws2-ddb:application-json")
                        .to("aws2-ddb://activeTable?operation=UpdateItem");

                from("direct:deleteItem")
                        .setProperty("operation", constant(Ddb2Operations.DeleteItem.name()))
                        .transformDataType(null, "aws2-ddb:application-json")
                        .to("aws2-ddb://activeTable?operation=DeleteItem");
            }
        };
    }

    @Test
    public void testPutItem() {
        String json = "{\"id\": 1234, \"year\": 1977, \"title\": \"Star Wars IV\"}";

        template.sendBody("direct:putItem", json);

        assertNotNull(ddbClient.putItemRequest);
        assertEquals("activeTable", ddbClient.putItemRequest.tableName());
        assertNotNull(ddbClient.putItemRequest.item());
        assertEquals("1234", ddbClient.putItemRequest.item().get("id").n());
        assertEquals("1977", ddbClient.putItemRequest.item().get("year").n());
        assertEquals("Star Wars IV", ddbClient.putItemRequest.item().get("title").s());
    }

    @Test
    public void testUpdateItem() {
        String json = "{\"key\": {\"id\": 1234}, \"item\": {\"title\": \"King Kong - Historical\", \"year\": 1933}}";

        template.sendBody("direct:updateItem", json);

        assertNotNull(ddbClient.updateItemRequest);
        assertEquals("activeTable", ddbClient.updateItemRequest.tableName());

        assertNotNull(ddbClient.updateItemRequest.key());
        assertEquals("1234", ddbClient.updateItemRequest.key().get("id").n());

        assertNotNull(ddbClient.updateItemRequest.attributeUpdates());
        assertEquals("King Kong - Historical",
                ddbClient.updateItemRequest.attributeUpdates().get("title").value().s());
        assertEquals("1933",
                ddbClient.updateItemRequest.attributeUpdates().get("year").value().n());
    }

    @Test
    public void testDeleteItem() {
        String json = "{\"key\": {\"id\": 1234}}";

        template.sendBody("direct:deleteItem", json);

        assertNotNull(ddbClient.deleteItemRequest);
        assertEquals("activeTable", ddbClient.deleteItemRequest.tableName());

        assertNotNull(ddbClient.deleteItemRequest.key());
        assertEquals("1234", ddbClient.deleteItemRequest.key().get("id").n());
    }

    @Test
    public void testUpdateItemWithArrayValues() {
        String json
                = "{\"key\": {\"id\": 5678}, \"item\": {\"title\": \"King Kong\", \"year\": 1933, \"directors\": [\"Merian C. Cooper\", \"Ernest B. Schoedsack\"]}}";

        template.sendBody("direct:updateItem", json);

        assertNotNull(ddbClient.updateItemRequest);
        assertEquals("activeTable", ddbClient.updateItemRequest.tableName());
        assertEquals("5678", ddbClient.updateItemRequest.key().get("id").n());

        assertNotNull(ddbClient.updateItemRequest.attributeUpdates().get("directors"));
        assertEquals(2, ddbClient.updateItemRequest.attributeUpdates().get("directors").value().ss().size());
    }
}
