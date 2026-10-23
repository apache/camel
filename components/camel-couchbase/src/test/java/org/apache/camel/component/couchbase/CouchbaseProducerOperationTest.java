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
package org.apache.camel.component.couchbase;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.codec.RawBinaryTranscoder;
import com.couchbase.client.java.codec.RawJsonTranscoder;
import com.couchbase.client.java.kv.GetOptions;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.kv.UpsertOptions;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The document id, the operation and the way a CCB_PUT body is stored, with the producer in a real route against a
 * mocked cluster.
 */
class CouchbaseProducerOperationTest {

    private static final String URI = "couchbase:http://localhost:8091?bucket=bucket&username=user&password=secret";

    private DefaultCamelContext context;
    private MockedStatic<Cluster> clusters;
    private Collection collection;

    @BeforeEach
    void setUp() {
        Bucket bucket = mock(Bucket.class);
        collection = mock(Collection.class);
        when(bucket.defaultScope()).thenReturn(mock(Scope.class));
        when(bucket.defaultCollection()).thenReturn(collection);
        when(collection.upsert(anyString(), any(), any(UpsertOptions.class))).thenReturn(mock(MutationResult.class));
        Cluster cluster = mock(Cluster.class);
        when(cluster.bucket(anyString())).thenReturn(bucket);
        clusters = mockStatic(Cluster.class);
        clusters.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(cluster);
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        clusters.close();
    }

    private ProducerTemplate startRoute(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(uri);
            }
        });
        context.start();
        return context.createProducerTemplate();
    }

    /** The upsert options of the one upsert of the given id. */
    private UpsertOptions.Built upsertOf(String id, Object content) {
        ArgumentCaptor<UpsertOptions> options = ArgumentCaptor.forClass(UpsertOptions.class);
        verify(collection).upsert(eq(id), eq(content), options.capture());
        return options.getValue().build();
    }

    private Object upsertedContent(String id) {
        ArgumentCaptor<Object> content = ArgumentCaptor.forClass(Object.class);
        verify(collection).upsert(eq(id), content.capture(), any(UpsertOptions.class));
        return content.getValue();
    }

    @Test
    void theKeyOptionIsTheIdWithoutTheHeader() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=from-option");

        template.sendBody("direct:start", "Key option");

        verify(collection).upsert(eq("from-option"), eq("Key option"), any(UpsertOptions.class));
    }

    @Test
    void theHeaderWinsOverTheKeyOption() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=from-option");

        template.sendBodyAndHeader("direct:start", "Header", HEADER_ID, "from-header");

        verify(collection).upsert(eq("from-header"), eq("Header"), any(UpsertOptions.class));
        verify(collection, never()).upsert(eq("from-option"), any(), any(UpsertOptions.class));
    }

    @Test
    void withoutHeaderAndKeyOptionTheCallFails() throws Exception {
        ProducerTemplate template = startRoute(URI);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "No id"));

        CouchbaseException cause = assertInstanceOf(CouchbaseException.class, e.getCause());
        assertTrue(cause.getMessage().contains(HEADER_ID), cause.getMessage());
        assertTrue(cause.getMessage().contains("key option"), cause.getMessage());
        verify(collection, never()).upsert(anyString(), any(), any(UpsertOptions.class));
    }

    @Test
    void aGetAfterAPutInTheSameRouteReadsTheSameDocument() throws Exception {
        GetResult document = mock(GetResult.class);
        when(collection.get(eq("doc-1"), any(GetOptions.class))).thenReturn(document);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(URI).to(URI + "&operation=CCB_GET");
            }
        });
        context.start();

        Object body = context.createProducerTemplate().requestBodyAndHeader("direct:start", "Hello", HEADER_ID, "doc-1");

        assertSame(document, body);
        verify(collection).upsert(eq("doc-1"), eq("Hello"), any(UpsertOptions.class));
    }

    @Test
    void anUnknownOperationFailsAtStart() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(URI + "&key=doc-1&operation=ccb_get");
            }
        });

        Exception e = assertThrows(FailedToStartRouteException.class, () -> context.start());

        Throwable cause = e;
        while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("ccb_get"), cause.getMessage());
        assertTrue(cause.getMessage().contains("CCB_PUT, CCB_GET, CCB_DEL"), cause.getMessage());
    }

    @Test
    void jsonTextIsStoredAsJson() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=j1");
        String json = " {\"id\":\"j1\",\"name\":\"Camel\"}\n";

        template.sendBody("direct:start", json);

        assertSame(RawJsonTranscoder.INSTANCE, upsertOf("j1", json).transcoder());
    }

    @Test
    void aJsonArrayAsTextIsStoredAsJson() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=a1");
        String json = "[1, 2, {\"three\": 3}]";

        template.sendBody("direct:start", json);

        assertSame(RawJsonTranscoder.INSTANCE, upsertOf("a1", json).transcoder());
    }

    @Test
    void otherTextIsStoredAsAJsonString() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=t1");

        template.sendBody("direct:start", "{not json}");

        // no transcoder in the options: the one of the environment, the JSON transcoder, writes a JSON string
        assertNull(upsertOf("t1", "{not json}").transcoder());
    }

    @Test
    void jsonBytesAreStoredAsJson() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=b1");
        byte[] json = "{\"id\":\"b1\"}".getBytes(StandardCharsets.UTF_8);

        template.sendBody("direct:start", json);

        assertSame(RawJsonTranscoder.INSTANCE, upsertOf("b1", json).transcoder());
    }

    @Test
    void otherBytesAreStoredAsBinary() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=b2");
        byte[] data = { (byte) 0xCA, (byte) 0xFE, 0x00, 0x7B };

        template.sendBody("direct:start", data);

        assertSame(RawBinaryTranscoder.INSTANCE, upsertOf("b2", data).transcoder());
    }

    @Test
    void aStreamIsReadAsBytesAndStoredAsBinary() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=s1");
        byte[] data = { 0x01, 0x02, 0x03 };

        template.sendBody("direct:start", new ByteArrayInputStream(data));

        Object content = upsertedContent("s1");
        assertArrayEquals(data, assertInstanceOf(byte[].class, content));
        assertSame(RawBinaryTranscoder.INSTANCE, upsertOf("s1", content).transcoder());
    }

    @Test
    void aStreamHoldingJsonIsStoredAsJson() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=s2");
        byte[] json = "{\"id\":\"s2\"}".getBytes(StandardCharsets.UTF_8);

        template.sendBody("direct:start", new ByteArrayInputStream(json));

        Object content = upsertedContent("s2");
        assertArrayEquals(json, assertInstanceOf(byte[].class, content));
        assertSame(RawJsonTranscoder.INSTANCE, upsertOf("s2", content).transcoder());
    }

    @Test
    void aMapIsSerializedAsBefore() throws Exception {
        ProducerTemplate template = startRoute(URI + "&key=m1");
        Map<String, Object> map = Map.of("name", "Camel");

        Object result = template.requestBody("direct:start", map);

        assertEquals(Boolean.TRUE, result);
        assertNull(upsertOf("m1", map).transcoder());
    }
}
