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
package org.apache.camel.component.couchdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.ibm.cloud.cloudant.v1.model.ChangesResult;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.cloudant.v1.model.DocumentResult;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.util.GsonSingleton;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The producer acts on the method the couchdb consumer sets, reads the body only for the methods that use it, and says
 * what is wrong with a body it cannot save.
 */
class CouchDbProducerMethodAndBodyTest extends CamelTestSupport {

    // CouchDB sets "deleted" only on the changes of deleted documents
    private static final String UPDATE = "{\"seq\":\"%s\",\"id\":\"%s\",\"changes\":[{\"rev\":\"1-a\"}]}";
    private static final String DELETE = "{\"seq\":\"%s\",\"id\":\"%s\",\"changes\":[{\"rev\":\"2-b\"}],\"deleted\":true}";

    private final CouchDbClientWrapper client = mock(CouchDbClientWrapper.class);

    @Test
    void consumerUpdateChangeIsSavedByProducer() throws Exception {
        stubSave("saved-1", "1-s");

        List<Exchange> processed = consumeIntoProducer(UPDATE.formatted("1-x", "doc1"));

        assertEquals(1, processed.size());
        Exchange exchange = processed.get(0);
        assertNull(exchange.getException());
        // the producer saved the change, as the consumer's UPDATE method is a save
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(client).save(saved.capture());
        JsonObject document = JsonParser.parseString(saved.getValue().toString()).getAsJsonObject();
        assertEquals("doc1", document.get("id").getAsString());
        assertEquals("saved-1", exchange.getIn().getHeader(CouchDbConstants.HEADER_DOC_ID));
        assertEquals("1-s", exchange.getIn().getHeader(CouchDbConstants.HEADER_DOC_REV));
    }

    @Test
    void consumerDeleteChangeFailsAsItIsNotADocument() throws Exception {
        List<Exchange> processed = consumeIntoProducer(DELETE.formatted("1-x", "doc1"));

        assertEquals(1, processed.size());
        Exception error = processed.get(0).getException();
        assertInstanceOf(CouchDbException.class, error);
        assertTrue(error.getMessage().contains("must hold the _id and _rev of the document to delete"), error.getMessage());
        verify(client, never()).removeByIdAndRev(any(), any());
    }

    @Test
    void unknownMethodFailsAndWritesNothing() {
        Exchange exchange = exchange("INSERT", "{ \"name\" : \"coldplay\" }");

        CouchDbException error = assertThrows(CouchDbException.class, () -> createProducer().process(exchange));

        assertTrue(error.getMessage().contains("Unsupported CamelCouchDbMethod header: INSERT"), error.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void getReadsTheDocumentWithoutABody() throws Exception {
        Document document = stubGet("doc1");
        Exchange exchange = exchange("GET", null);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, "doc1");

        createProducer().process(exchange);

        assertSame(document, exchange.getIn().getBody());
    }

    @Test
    void getDoesNotParseTheBody() throws Exception {
        Document document = stubGet("doc1");
        Exchange exchange = exchange("GET", "not { json");
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, "doc1");

        createProducer().process(exchange);

        assertSame(document, exchange.getIn().getBody());
    }

    @Test
    void invalidJsonTextFailsWithTheParseErrorAsCause() {
        Exchange exchange = exchange(null, "{ \"password\" : \"s3cret\"");

        InvalidPayloadException error
                = assertThrows(InvalidPayloadException.class, () -> createProducer().process(exchange));

        assertTrue(error.getMessage().contains("the body is not valid JSON"), error.getMessage());
        assertInstanceOf(JsonSyntaxException.class, error.getCause());
        assertFalse(error.getMessage().contains("s3cret"), error.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void jsonArrayFailsSayingTheBodyMustBeOneObject() {
        JsonArray documents = new JsonArray();
        documents.add(JsonParser.parseString("{ \"name\" : \"coldplay\" }"));
        documents.add(JsonParser.parseString("{ \"name\" : \"muse\" }"));

        for (Object body : new Object[] { documents.toString(), documents }) {
            Exchange exchange = exchange(null, body);

            CouchDbException error = assertThrows(CouchDbException.class, () -> createProducer().process(exchange));

            assertTrue(
                    error.getMessage().contains("The body must be one JSON object to save a document, but it is a JSON array"),
                    error.getMessage());
        }
        verifyNoInteractions(client);
    }

    @Test
    void deleteOfAJsonArrayFailsWithoutTheBodyInTheMessage() {
        Exchange exchange = exchange("DELETE", "[{ \"_id\" : \"doc1\", \"_rev\" : \"1-a\", \"password\" : \"s3cret\" }]");

        CouchDbException error = assertThrows(CouchDbException.class, () -> createProducer().process(exchange));

        assertTrue(error.getMessage().contains("The body must be one JSON object to delete a document"), error.getMessage());
        assertFalse(error.getMessage().contains("s3cret"), error.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void mapBodyFailsNamingTheBodiesItTakes() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "coldplay");
        Exchange exchange = exchange(null, body);

        InvalidPayloadException error
                = assertThrows(InvalidPayloadException.class, () -> createProducer().process(exchange));

        assertEquals(JsonElement.class, error.getType());
        assertTrue(error.getMessage().contains("JSON text, a Gson JsonElement or a Cloudant Document"), error.getMessage());
        verifyNoInteractions(client);
    }

    /**
     * A route from a couchdb consumer to a couchdb producer: the consumer polls the changes and hands each exchange,
     * with its CamelCouchDbMethod header, to the producer.
     */
    private List<Exchange> consumeIntoProducer(String... changes) throws Exception {
        CouchDbClientWrapper source = mock(CouchDbClientWrapper.class);
        when(source.getLatestUpdateSequence()).thenReturn("0");
        Response<ChangesResult> page = changes(changes);
        when(source.pollChanges(anyString(), anyString(), anyLong(), anyLong())).thenReturn(page);

        CouchDbProducer producer = createProducer();
        List<Exchange> processed = new CopyOnWriteArrayList<>();
        CouchDbEndpoint endpoint = context.getEndpoint("couchdb:http://localhost:5984/source", CouchDbEndpoint.class);
        CouchDbConsumer consumer = new CouchDbConsumer(endpoint, source, exchange -> {
            try {
                producer.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            processed.add(exchange);
        });
        consumer.setMaxMessagesPerPoll(endpoint.getMaxMessagesPerPoll());
        consumer.init();
        consumer.poll();
        return processed;
    }

    private CouchDbProducer createProducer() {
        return new CouchDbProducer(context.getEndpoint("couchdb:http://localhost:5984/target", CouchDbEndpoint.class), client);
    }

    private Exchange exchange(String method, Object body) {
        Exchange exchange = new DefaultExchange(context);
        if (method != null) {
            exchange.getIn().setHeader(CouchDbConstants.HEADER_METHOD, method);
        }
        exchange.getIn().setBody(body);
        return exchange;
    }

    @SuppressWarnings("unchecked")
    private void stubSave(String id, String rev) {
        DocumentResult result = GsonSingleton.getGson()
                .fromJson("{\"ok\":true,\"id\":\"" + id + "\",\"rev\":\"" + rev + "\"}", DocumentResult.class);
        Response<DocumentResult> response = mock(Response.class);
        when(response.getResult()).thenReturn(result);
        when(client.save(any(Document.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private Document stubGet(String id) {
        Document document = new Document.Builder().id(id).add("name", "coldplay").build();
        Response<Document> response = mock(Response.class);
        when(response.getResult()).thenReturn(document);
        when(client.get(id)).thenReturn(response);
        return document;
    }

    @SuppressWarnings("unchecked")
    private static Response<ChangesResult> changes(String... items) {
        String json = "{\"last_seq\":\"9-x\",\"pending\":0,\"results\":[" + String.join(",", items) + "]}";
        ChangesResult result = GsonSingleton.getGson().fromJson(json, ChangesResult.class);
        Response<ChangesResult> response = mock(Response.class);
        when(response.getResult()).thenReturn(result);
        return response;
    }
}
