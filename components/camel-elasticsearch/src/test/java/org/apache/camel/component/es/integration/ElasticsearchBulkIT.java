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
package org.apache.camel.component.es.integration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.CreateOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertCollectionSize;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchBulkIT extends ElasticsearchTestSupport {

    @Test
    void testBulkWithMap() {
        List<Map<String, String>> documents = new ArrayList<>();
        Map<String, String> document1 = createIndexedData("1");
        Map<String, String> document2 = createIndexedData("2");

        documents.add(document1);
        documents.add(document2);

        List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
        assertNotNull(indexIds, "indexIds should be set");
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    void testBulkWithString() {
        List<String> documents = List.of(
                "{\"testBulkWithString1\": \"some-value\"}", "{\"testBulkWithString2\": \"some-value\"}");

        List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
        assertNotNull(indexIds, "indexIds should be set");
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    void testBulkWithBytes() {
        List<byte[]> documents = List.of(
                "{\"testBulkWithBytes1\": \"some-value\"}".getBytes(StandardCharsets.UTF_8),
                "{\"testBulkWithBytes2\": \"some-value\"}".getBytes(StandardCharsets.UTF_8));

        List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
        assertNotNull(indexIds, "indexIds should be set");
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    void testBulkWithReader() {
        List<Reader> documents = List.of(
                new StringReader("{\"testBulkWithReader1\": \"some-value\"}"),
                new StringReader("{\"testBulkWithReader2\": \"some-value\"}"));

        List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
        assertNotNull(indexIds, "indexIds should be set");
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    void testBulkWithInputStream() {
        List<InputStream> documents = List.of(
                new ByteArrayInputStream(
                        "{\"testBulkWithInputStream1\": \"some-value\"}".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(
                        "{\"testBulkWithInputStream2\": \"some-value\"}".getBytes(StandardCharsets.UTF_8)));

        List<?> indexIds = template.requestBody("direct:bulk", documents, List.class);
        assertNotNull(indexIds, "indexIds should be set");
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    void testBulkListRequestBody() {
        String prefix = createPrefix();

        // given
        List<Map<String, String>> request = new ArrayList<>();
        final HashMap<String, String> valueMap = new HashMap<>();
        valueMap.put("id", prefix + "baz");
        valueMap.put("content", prefix + "hello");
        request.add(valueMap);
        // when
        List<?> indexedDocumentIds = template.requestBody("direct:bulk", request, List.class);

        // then
        assertThat(indexedDocumentIds, notNullValue());
        assertThat(indexedDocumentIds.size(), equalTo(1));
    }

    @Test
    void testBulkRequestBody() {
        String prefix = createPrefix();

        // given
        BulkRequest.Builder builder = new BulkRequest.Builder();
        builder.operations(
                new BulkOperation.Builder()
                        .index(new IndexOperation.Builder<>().index(prefix + "foo").id(prefix + "baz")
                                .document(Map.of(prefix + "content", prefix + "hello")).build())
                        .build());

        // when
        @SuppressWarnings("unchecked")
        List<BulkResponseItem> response = template.requestBody("direct:bulk", builder, List.class);

        // then
        assertThat(response, notNullValue());
        assertThat(response.size(), equalTo(1));
        assertThat(response.get(0).error(), nullValue());
        assertThat(response.get(0).id(), equalTo(prefix + "baz"));
    }

    @Test
    void bulkRequestBody() {
        String prefix = createPrefix();

        // given
        BulkRequest.Builder builder = new BulkRequest.Builder();
        builder.operations(
                new BulkOperation.Builder()
                        .index(new IndexOperation.Builder<>().index(prefix + "foo").id(prefix + "baz")
                                .document(Map.of(prefix + "content", prefix + "hello")).build())
                        .build());
        // when
        @SuppressWarnings("unchecked")
        List<BulkResponseItem> response = template.requestBody("direct:bulk", builder, List.class);

        // then
        assertThat(response, notNullValue());
        assertEquals(prefix + "baz", response.get(0).id());
    }

    @Test
    void bulkDeleteOperation() {
        // given
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        DeleteOperation.Builder builder = new DeleteOperation.Builder().index("twitter").id(indexId);
        // when
        @SuppressWarnings("unchecked")
        List<BulkResponseItem> response = template.requestBody("direct:bulk", List.of(builder), List.class);

        // then
        assertThat(response, notNullValue());
        assertEquals(indexId, response.get(0).id());
        GetResponse<?> resp = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(resp, "response should not be null");
        assertNull(resp.source(), "response source should be null");
    }

    @Test
    void bulkCreateOperation() {
        // given
        String prefix = createPrefix();

        CreateOperation.Builder<?> builder
                = new CreateOperation.Builder<>().index("twitter").document(Map.of(prefix + "content", prefix + "hello"));
        // when
        @SuppressWarnings("unchecked")
        List<BulkResponseItem> response = template.requestBody("direct:bulk", List.of(builder), List.class);

        // then
        assertThat(response, notNullValue());
        GetResponse<?> resp = template.requestBody("direct:get", response.get(0).id(), GetResponse.class);
        assertNotNull(resp, "response should not be null");
        assertNotNull(resp.source(), "response source should not be null");
    }

    @Test
    void bulkUpdateOperation() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        UpdateOperation.Builder<?, ?> builder = new UpdateOperation.Builder<>()
                .index("twitter").id(indexId)
                .action(
                        new UpdateAction.Builder<>()
                                .doc(JsonData.from(
                                        new StringReader(
                                                String.format("{\"%skey2\": \"%svalue2\"}",
                                                        createPrefix(), createPrefix()))))
                                .build());
        @SuppressWarnings("unchecked")
        List<BulkResponseItem> response = template.requestBody("direct:bulk", List.of(builder), List.class);

        //now, verify GET succeeded
        assertThat(response, notNullValue());
        GetResponse<?> resp = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull(resp, "response should not be null");
        assertNotNull(resp.source(), "response source should not be null");
        assertInstanceOf(ObjectNode.class, resp.source(), "response source should be a ObjectNode");
        assertTrue(((ObjectNode) resp.source()).has(createPrefix() + "key2"));
        assertEquals(createPrefix() + "value2", ((ObjectNode) resp.source()).get(createPrefix() + "key2").asText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index")
                        .to("elasticsearch://elasticsearch?operation=Index&indexName=twitter");
                from("direct:get")
                        .to("elasticsearch://elasticsearch?operation=GetById&indexName=twitter");
                from("direct:bulk")
                        .to("elasticsearch://elasticsearch?operation=Bulk&indexName=twitter");
            }
        };
    }
}
