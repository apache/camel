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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class ElasticsearchSizeLimitIT extends ElasticsearchTestSupport {

    @Test
    void testSize() {
        //put 4
        template.requestBody("direct:index", getContent("content"), String.class);
        template.requestBody("direct:index", getContent("content1"), String.class);
        template.requestBody("direct:index", getContent("content2"), String.class);
        template.requestBody("direct:index", getContent("content3"), String.class);

        String query = "{\"query\":{\"match_all\": {}}}";

        // the result may see stale data so use Awaitility
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
            HitsMetadata<?> searchWithSizeTwo = template.requestBody("direct:searchWithSizeTwo", query, HitsMetadata.class);
            HitsMetadata<?> searchFrom3 = template.requestBody("direct:searchFrom3", query, HitsMetadata.class);
            return searchWithSizeTwo.hits().size() == 2 && searchFrom3.hits().size() == 1;
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index")
                        .to("elasticsearch://elasticsearch?operation=Index&indexName=size-limit");
                from("direct:searchWithSizeTwo")
                        .to("elasticsearch://elasticsearch?operation=Search&indexName=size-limit&size=2");
                from("direct:searchFrom3")
                        .to("elasticsearch://elasticsearch?operation=Search&indexName=size-limit&from=3");
            }
        };
    }

    private Map<String, String> getContent(String content) {
        Map<String, String> map = new HashMap<>();
        map.put("content", content);
        return map;
    }
}
