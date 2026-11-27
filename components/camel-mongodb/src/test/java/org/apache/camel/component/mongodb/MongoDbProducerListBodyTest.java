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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.apache.camel.component.mongodb.MongoDbConstants.CRITERIA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The update, findOneAndUpdate and aggregate operations read a byte[], InputStream, StreamCache or File body, such as
 * JSON text from a Kafka, HTTP or file consumer, as they read the same text as a String: a JSON array of the criteria
 * and the update, the update when the criteria are in a header, or the pipeline or a single stage.
 */
class MongoDbProducerListBodyTest extends CamelTestSupport {

    private final MongoCollection<Document> collection = mock(MongoCollection.class);

    @Test
    void testUpdateByteArrayBodyWithCriteriaAndUpdate() {
        String body = "[{\"name\": \"order-1\"}, {\"$set\": {\"qty\": 3}}]";
        template.requestBody("direct:update", body.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Bson> criteria = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(criteria.capture(), update.capture(), any(UpdateOptions.class));
        assertEquals("order-1", toBsonDocument(criteria.getValue()).getString("name").getValue());
        assertEquals(3, toBsonDocument(update.getValue()).getDocument("$set").getInt32("qty").getValue());
    }

    @Test
    void testUpdateByteArrayBodyWithCriteriaHeader() {
        String body = "{\"$set\": {\"qty\": 3}}";
        template.requestBodyAndHeader("direct:update", body.getBytes(StandardCharsets.UTF_8), CRITERIA,
                Filters.eq("name", "order-1"));

        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(any(Bson.class), update.capture(), any(UpdateOptions.class));
        assertEquals(3, toBsonDocument(update.getValue()).getDocument("$set").getInt32("qty").getValue());
    }

    @Test
    void testFindOneAndUpdateByteArrayBodyWithCriteriaAndUpdate() {
        String body = "[{\"name\": \"order-1\"}, {\"$set\": {\"qty\": 3}}]";
        template.requestBody("direct:findOneAndUpdate", body.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Bson> criteria = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).findOneAndUpdate(criteria.capture(), update.capture(), any(FindOneAndUpdateOptions.class));
        assertEquals("order-1", toBsonDocument(criteria.getValue()).getString("name").getValue());
        assertEquals(3, toBsonDocument(update.getValue()).getDocument("$set").getInt32("qty").getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAggregateByteArrayPipeline() {
        String body = "[{\"$match\": {\"qty\": 3}}, {\"$limit\": 5}]";
        template.requestBody("direct:aggregate", body.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<List<Bson>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(collection).aggregate(pipeline.capture());
        assertEquals(2, pipeline.getValue().size());
        assertEquals(3, toBsonDocument(pipeline.getValue().get(0)).getDocument("$match").getInt32("qty").getValue());
        assertEquals(5, toBsonDocument(pipeline.getValue().get(1)).getInt32("$limit").getValue());
    }

    @Test
    void testUpdateInputStreamBodyWithCriteriaAndUpdate() {
        String body = "[{\"name\": \"order-1\"}, {\"$set\": {\"qty\": 3}}]";
        // a route without stream caching, so that the body reaches the producer as a plain InputStream
        template.requestBody("direct:updateNoStreamCaching", new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        ArgumentCaptor<Bson> criteria = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(criteria.capture(), update.capture(), any(UpdateOptions.class));
        assertEquals("order-1", toBsonDocument(criteria.getValue()).getString("name").getValue());
        assertEquals(3, toBsonDocument(update.getValue()).getDocument("$set").getInt32("qty").getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAggregateStreamCachePipelineEndingWithNewline() {
        // as a file or an HTTP request delivers it, with stream caching, and with a trailing newline
        String body = "[{\"$match\": {\"qty\": 3}}, {\"$limit\": 5}]\n";
        template.requestBody("direct:aggregate",
                new ByteArrayInputStreamCache(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));

        ArgumentCaptor<List<Bson>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(collection).aggregate(pipeline.capture());
        assertEquals(2, pipeline.getValue().size());
        assertEquals(5, toBsonDocument(pipeline.getValue().get(1)).getInt32("$limit").getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAggregateStreamCacheBodyWithASingleStage() {
        // a single stage is not a JSON array: the fallback converts the same text, the stream is not read twice
        String body = "{\"$match\": {\"qty\": 3}}";
        template.requestBody("direct:aggregate",
                new ByteArrayInputStreamCache(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));

        ArgumentCaptor<List<Bson>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(collection).aggregate(pipeline.capture());
        assertEquals(1, pipeline.getValue().size());
        assertEquals(3, toBsonDocument(pipeline.getValue().get(0)).getDocument("$match").getInt32("qty").getValue());
    }

    @Test
    void testUpdateFileBodyWithCriteriaAndUpdate(@TempDir Path dir) throws Exception {
        // as a file consumer delivers it
        Path file = dir.resolve("update.json");
        Files.writeString(file, "[{\"name\": \"order-1\"}, {\"$set\": {\"qty\": 3}}]\n");
        template.requestBody("direct:update", file.toFile());

        ArgumentCaptor<Bson> criteria = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(criteria.capture(), any(Bson.class), any(UpdateOptions.class));
        assertEquals("order-1", toBsonDocument(criteria.getValue()).getString("name").getValue());
    }

    @Test
    void testUpdateStringBodyWithSurroundingWhitespace() {
        String body = "\n  [{\"name\": \"order-1\"}, {\"$set\": {\"qty\": 3}}]\r\n";
        template.requestBody("direct:update", body);

        ArgumentCaptor<Bson> criteria = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(criteria.capture(), any(Bson.class), any(UpdateOptions.class));
        assertEquals("order-1", toBsonDocument(criteria.getValue()).getString("name").getValue());
    }

    @Test
    void testMalformedByteArrayBodyKeepsTheParseError() {
        byte[] body = "[{\"name\": }]".getBytes(StandardCharsets.UTF_8);
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:update", body));

        boolean payload = false;
        boolean found = false;
        for (Throwable c = e.getCause(); c != null && !found; c = c.getCause()) {
            payload |= c instanceof InvalidPayloadException;
            found = c instanceof JsonParseException;
        }
        assertTrue(payload, "an InvalidPayloadException should be in the cause chain");
        assertTrue(found, "the JsonParseException should be in the cause chain");
    }

    @Test
    void testUpdateBodyThatIsNotAnArrayFailsWithTheSizeError() {
        // a String that is not a JSON array does not convert to a List; it failed with a NullPointerException
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:update", "{\"name\": \"order-1\"}"));

        CamelMongoDbException cause = assertInstanceOf(CamelMongoDbException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("MongoDB operation = update,"), cause.getMessage());
    }

    @Test
    void testUpdateBodyOfWrongSizeNamesTheUpdateOperation() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:update", "[{\"name\": \"order-1\"}]"));

        CamelMongoDbException cause = assertInstanceOf(CamelMongoDbException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("MongoDB operation = update,"), cause.getMessage());
    }

    private static BsonDocument toBsonDocument(Bson bson) {
        return bson.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        when(collection.withWriteConcern(any())).thenReturn(collection);
        when(collection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        AggregateIterable<Document> aggregation = mock(AggregateIterable.class);
        when(aggregation.iterator()).thenReturn(mock(MongoCursor.class));
        when(collection.aggregate(anyList())).thenReturn(aggregation);

        final MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection(anyString(), eq(Document.class))).thenReturn(collection);

        final MongoClient client = mock(MongoClient.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(client.listDatabaseNames()).thenReturn(mock(MongoIterable.class));

        context.getRegistry().bind("mongoClient", client);
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:update")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=update&dynamicity=false");
                from("direct:findOneAndUpdate")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=findOneAndUpdate&dynamicity=false");
                from("direct:updateNoStreamCaching").noStreamCaching()
                        .to("mongodb:mongoClient?database=test&collection=c&operation=update&dynamicity=false");
                from("direct:aggregate")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=aggregate&dynamicity=false");
            }
        };
    }
}
