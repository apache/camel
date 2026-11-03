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
package org.apache.camel.component.azure.cosmosdb;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosItemSerializer;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The body of createItem, upsertItem and replaceItem is one item. The SDK serializes a Map or a POJO with Jackson, but
 * writes a String (or a file, as its path) as a JSON string and cannot serialize a stream: the JSON of the item, as
 * text, bytes, a stream or a file, is parsed into a Map first. A List of items, or JSON that is not one object, fails
 * before anything is written.
 */
class CosmosDbProducerItemBodyTest extends CamelTestSupport {

    private static final String JSON = "{\"id\":\"1\",\"pk\":\"a\",\"city\":\"Zürich\",\"price\":9.5,\"tags\":[\"x\",\"y\"]}";

    @BindToRegistry("cosmosClient")
    private final CosmosAsyncClient client = mock(CosmosAsyncClient.class);
    private final CosmosAsyncDatabase database = mock(CosmosAsyncDatabase.class);
    private final CosmosAsyncContainer container = mock(CosmosAsyncContainer.class);
    // the items given to the SDK
    private final List<Object> written = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createItem").to("azure-cosmosdb:myDb/items?operation=createItem&itemPartitionKey=a");
                from("direct:upsertItem").to("azure-cosmosdb:myDb/items?operation=upsertItem&itemPartitionKey=a");
                from("direct:replaceItem")
                        .to("azure-cosmosdb:myDb/items?operation=replaceItem&itemPartitionKey=a&itemId=1");
            }
        };
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void mockContainer() {
        CosmosItemResponse<Object> response = mock(CosmosItemResponse.class);
        when(response.getStatusCode()).thenReturn(201);
        when(response.getItem()).thenAnswer(invocation -> written.get(written.size() - 1));

        when(client.getDatabase("myDb")).thenReturn(database);
        when(database.getContainer("items")).thenReturn(container);
        when(container.createItem(any(), any(PartitionKey.class), any())).thenAnswer(invocation -> {
            written.add(invocation.getArgument(0));
            return Mono.just(response);
        });
        when(container.upsertItem(any(), any(PartitionKey.class), any())).thenAnswer(invocation -> {
            written.add(invocation.getArgument(0));
            return Mono.just(response);
        });
        when(container.replaceItem(any(), anyString(), any(PartitionKey.class), any())).thenAnswer(invocation -> {
            written.add(invocation.getArgument(0));
            return Mono.just(response);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "createItem", "upsertItem", "replaceItem" })
    void jsonTextIsWrittenAsAnObject(String operation) {
        Exchange exchange = template.send("direct:" + operation, e -> e.getIn().setBody(JSON));

        assertNull(exchange.getException());
        Map<?, ?> item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", document(item).get("id"));
        assertEquals("Zürich", item.get("city"));
        assertEquals(9.5, item.get("price"));
        assertEquals(List.of("x", "y"), item.get("tags"));
        // the body is the item as written
        assertSame(item, exchange.getMessage().getBody());
        assertEquals(201, exchange.getMessage().getHeader(CosmosDbConstants.STATUS_CODE));
    }

    @Test
    void jsonBytesAreParsed() {
        Exchange exchange = template.send("direct:createItem", e -> e.getIn().setBody(JSON.getBytes(StandardCharsets.UTF_8)));

        assertNull(exchange.getException());
        Map<?, ?> item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", item.get("id"));
        assertEquals("Zürich", item.get("city"));
    }

    @Test
    void streamIsParsed() {
        Exchange exchange = template.send("direct:createItem",
                e -> e.getIn().setBody(new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8))));

        assertNull(exchange.getException());
        Map<?, ?> item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", document(item).get("id"));
        assertEquals("Zürich", item.get("city"));
    }

    @Test
    void readerIsParsed() {
        Exchange exchange = template.send("direct:upsertItem", e -> e.getIn().setBody(new StringReader(JSON)));

        assertNull(exchange.getException());
        Map<?, ?> item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", document(item).get("id"));
    }

    @Test
    void fileAndPathAreParsed(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("item.json"), JSON);

        Exchange exchange = template.send("direct:createItem", e -> e.getIn().setBody(file.toFile()));
        assertNull(exchange.getException());
        Map<?, ?> item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", document(item).get("id"));
        assertEquals("Zürich", item.get("city"));

        exchange = template.send("direct:replaceItem", e -> e.getIn().setBody(file));
        assertNull(exchange.getException());
        item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", document(item).get("id"));
    }

    @Test
    void fileOfTheFileConsumerIsParsed(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("item.json"), JSON);
        MockEndpoint mock = getMockEndpoint("mock:written");
        mock.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + dir.toAbsolutePath() + "?noop=true&initialDelay=0&delay=10")
                        .to("azure-cosmosdb:myDb/items?operation=createItem&itemPartitionKey=a")
                        .to("mock:written");
            }
        });

        mock.assertIsSatisfied();
        Map<?, ?> item = assertInstanceOf(Map.class, writtenItem());
        assertEquals("1", document(item).get("id"));
        assertEquals("Zürich", item.get("city"));
    }

    @Test
    void mapAndPojoAreWrittenAsTheyAre() {
        Map<String, Object> map = Map.of("id", "1", "pk", "a");
        template.sendBody("direct:createItem", map);
        assertSame(map, writtenItem());

        Item pojo = new Item("2", "a");
        template.sendBody("direct:upsertItem", pojo);
        assertSame(pojo, writtenItem());
    }

    @ParameterizedTest
    @ValueSource(strings = { "createItem", "upsertItem", "replaceItem" })
    void listOfItemsFails(String operation) {
        Exchange exchange = template.send("direct:" + operation,
                e -> e.getIn().setBody(List.of(Map.of("id", "1", "pk", "a"), Map.of("id", "2", "pk", "a"))));

        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains(operation + " operation must be one item"), error.getMessage());
        assertTrue(written.isEmpty());
    }

    @Test
    void jsonArrayFails() {
        Exchange exchange = template.send("direct:createItem", e -> e.getIn().setBody("[" + JSON + "," + JSON + "]"));

        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains("must be one item, but it is a JSON array"), error.getMessage());
        assertTrue(written.isEmpty());
    }

    @Test
    void jsonThatIsNotOneObjectFails() {
        Exchange exchange = template.send("direct:createItem", e -> e.getIn().setBody("\"some text\""));
        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains("must be a JSON object, but it is a JSON string"), error.getMessage());

        // two items as JSON lines: only the first would be written
        exchange = template.send("direct:createItem", e -> e.getIn().setBody(JSON + "\n" + JSON));
        error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains("more than one JSON value"), error.getMessage());

        assertTrue(written.isEmpty());
    }

    @Test
    void contentThatIsNotJsonFails() {
        Exchange exchange = template.send("direct:createItem", e -> e.getIn().setBody("id=1"));
        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains("createItem operation is not valid JSON"), error.getMessage());

        // 0xff is not UTF-8
        byte[] bytes = { '{', '"', 'i', 'd', '"', ':', '"', (byte) 0xff, '"', '}' };
        exchange = template.send("direct:createItem", e -> e.getIn().setBody(bytes));
        error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains("createItem operation is not valid JSON"), error.getMessage());

        assertTrue(written.isEmpty());
    }

    @Test
    void noBodyOrAnEmptyStreamFails() {
        Exchange exchange = template.send("direct:createItem", e -> e.getIn().setBody(null));
        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("item cannot be empty!", error.getMessage());

        exchange = template.send("direct:createItem", e -> e.getIn().setBody(new ByteArrayInputStream(new byte[0])));
        error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("item cannot be empty!", error.getMessage());

        assertTrue(written.isEmpty());
    }

    private Object writtenItem() {
        assertFalse(written.isEmpty(), "no item was written");
        return written.get(written.size() - 1);
    }

    // the fields of the document the SDK writes for the item, as its default serializer gives them
    private static Map<String, Object> document(Object item) {
        return CosmosItemSerializer.DEFAULT_SERIALIZER.serialize(item);
    }

    record Item(String id, String pk) {
    }
}
