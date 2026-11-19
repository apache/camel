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
package org.apache.camel.component.mongodb;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A byte[] body is an array, but a payload, not a batch: it is inserted as one document, like JSON text.
 */
class MongoDbProducerInsertBodyTest extends CamelTestSupport {

    private final MongoCollection<Document> collection = mock(MongoCollection.class);

    @Test
    void testInsertByteArrayBodyAsOneDocument() {
        template.requestBody("direct:insert", "{\"name\": \"order-1\", \"qty\": 2}".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(collection).insertOne(inserted.capture());
        assertEquals("order-1", inserted.getValue().getString("name"));
        assertEquals(2, inserted.getValue().getInteger("qty"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInsertObjectArrayBodyAsBatch() {
        Object[] batch = { new Document("name", "a"), new Document("name", "b") };
        template.requestBody("direct:insert", batch);

        ArgumentCaptor<List<Document>> inserted = ArgumentCaptor.forClass(List.class);
        verify(collection).insertMany(inserted.capture());
        assertEquals(2, inserted.getValue().size());
        assertEquals("b", inserted.getValue().get(1).getString("name"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        when(collection.withWriteConcern(any())).thenReturn(collection);

        final MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection(anyString(), eq(Document.class))).thenReturn(collection);

        final MongoClient client = mock(MongoClient.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(client.listDatabaseNames()).thenReturn(mock(com.mongodb.client.MongoIterable.class));

        context.getRegistry().bind("mongoClient", client);
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:insert")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=insert&dynamicity=false");
            }
        };
    }
}
