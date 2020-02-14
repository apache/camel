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
package org.apache.camel.component.elasticsearch;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.elasticsearch.search.SearchHits;
import org.junit.Ignore;
import org.junit.Test;

public class ElasticsearchSizeLimitTest extends ElasticsearchBaseTest {

    @Ignore("Looks like there were some assumption related to test executed before")
    @Test
    public void testSize() {
        //put 4
        String indexId = template.requestBody("direct:index", getContent("content"), String.class);
        String indexId1 = template.requestBody("direct:index", getContent("content1"), String.class);
        String indexId2 = template.requestBody("direct:index", getContent("content2"), String.class);
        String indexId4 = template.requestBody("direct:index", getContent("content3"), String.class);

        String query = "{\"query\":{\"match_all\": {}}}";

        // the result may see stale data so use Awaitility
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
            SearchHits searchWithSizeTwo = template.requestBody("direct:searchWithSizeTwo", query, SearchHits.class);
            SearchHits searchFrom3 = template.requestBody("direct:searchFrom3", query, SearchHits.class);
            return searchWithSizeTwo.getHits().length == 2 && searchFrom3.getHits().length == 1;
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index")
                    .to("elasticsearch-rest://elasticsearch?operation=Index&indexName=twitter");
                from("direct:searchWithSizeTwo")
                    .to("elasticsearch-rest://elasticsearch?operation=Search&indexName=twitter&size=2");
                from("direct:searchFrom3")
                    .to("elasticsearch-rest://elasticsearch?operation=Search&indexName=twitter&from=3");
            }
        };
    }

    private Map<String, String> getContent(String content) {
        Map<String, String> map = new HashMap<>();
        map.put("content", content);
        return map;
    }
}
