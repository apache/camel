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
package org.apache.camel.component.langchain4j.web.search;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A WebSearchRequest bean in the registry must not be used by an endpoint that does not refer to it.
 */
public class LangChain4jWebSearchAutowireTest extends CamelTestSupport {

    private final AtomicReference<WebSearchRequest> capturedRequest = new AtomicReference<>();

    private final WebSearchEngine capturingEngine = request -> {
        capturedRequest.set(request);
        return WebSearchResults.from(
                WebSearchInformationResult.from(2L),
                List.of(WebSearchOrganicResult.from("Title", URI.create("https://example.com"), "snippet", "content"),
                        WebSearchOrganicResult.from("Title 2", URI.create("https://example.org"), "snippet 2",
                                "content 2")));
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("engine", capturingEngine);
        // a bean that is meant for something else: the only WebSearchRequest of the registry
        context.getRegistry().bind("otherRequest",
                WebSearchRequest.builder().searchTerms("from the registry").maxResults(5).build());

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:search")
                        .to("langchain4j-web-search:test?webSearchEngine=#engine");

                from("direct:custom")
                        .to("langchain4j-web-search:custom?webSearchEngine=#engine&webSearchRequest=#otherRequest");
            }
        };
    }

    @Test
    void searchesTheBodyWhenAWebSearchRequestBeanIsInTheRegistry() {
        Object body = template.requestBody("direct:search", "apache camel");

        WebSearchRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("apache camel", request.searchTerms());
        assertEquals(1, request.maxResults());
        assertEquals("content", body);
    }

    @Test
    void usesTheWebSearchRequestItRefersTo() {
        Object body = template.requestBody("direct:custom", "apache camel");

        WebSearchRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("from the registry", request.searchTerms());
        assertEquals(List.of("content", "content 2"), body);
    }
}
