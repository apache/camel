/**
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
package org.apache.camel.component.elasticsearch5;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;

public class ElasticsearchBulkTest extends ElasticsearchBaseTest {

    @Test
    public void testBulkIndex() throws Exception {
        List<Map<String, String>> documents = new ArrayList<Map<String, String>>();
        Map<String, String> document1 = createIndexedData("1");
        Map<String, String> document2 = createIndexedData("2");

        documents.add(document1);
        documents.add(document2);

        List<?> indexIds = template.requestBody("direct:bulk_index", documents, List.class);
        assertNotNull("indexIds should be set", indexIds);
        assertCollectionSize("Indexed documents should match the size of documents", indexIds, documents.size());
    }

    @Test
    public void bulkIndexRequestBody() throws Exception {
        String prefix = createPrefix();

        // given
        BulkRequest request = new BulkRequest();
        request.add(new IndexRequest(prefix + "foo", prefix + "bar", prefix + "baz").source("{\"" + prefix + "content\": \"" + prefix + "hello\"}"));

        // when
        @SuppressWarnings("unchecked")
        List<String> indexedDocumentIds = template.requestBody("direct:bulk_index", request, List.class);

        // then
        assertThat(indexedDocumentIds, notNullValue());
        assertThat(indexedDocumentIds.size(), equalTo(1));
        assertThat(indexedDocumentIds, hasItem(prefix + "baz"));
    }

    @Test
    public void bulkRequestBody() throws Exception {
        String prefix = createPrefix();

        // given
        BulkRequest request = new BulkRequest();
        request.add(new IndexRequest(prefix + "foo", prefix + "bar", prefix + "baz").source("{\"" + prefix + "content\": \"" + prefix + "hello\"}"));

        // when
        BulkResponse response = template.requestBody("direct:bulk", request, BulkResponse.class);

        // then
        assertThat(response, notNullValue());
        assertEquals(prefix + "baz", response.getItems()[0].getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bulk_index").to("elasticsearch5://elasticsearch?operation=BULK_INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
                from("direct:bulk").to("elasticsearch5://elasticsearch?operation=BULK&indexName=twitter&indexType=tweet&ip=localhost&port=" + ES_TRANSPORT_PORT);
            }
        };
    }
}
