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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LangChain4jWebSearchSafeSearchTest extends CamelTestSupport {

    private final AtomicReference<WebSearchRequest> capturedRequest = new AtomicReference<>();

    private final WebSearchEngine capturingEngine = request -> {
        capturedRequest.set(request);
        return WebSearchResults.from(
                WebSearchInformationResult.from(1L),
                List.of(WebSearchOrganicResult.from(
                        "Title", URI.create("https://example.com"), "snippet", "content")));
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("capturingEngine", capturingEngine);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:search-false")
                        .to("langchain4j-web-search:test?webSearchEngine=#capturingEngine&safeSearch=false");

                from("direct:search-true")
                        .to("langchain4j-web-search:test?webSearchEngine=#capturingEngine&safeSearch=true");

                from("direct:search-with-params")
                        .to("langchain4j-web-search:test?webSearchEngine=#capturingEngine&safeSearch=false"
                            + "&maxResults=3&language=en&geoLocation=US");
            }
        };
    }

    @Test
    void safeSearchFalseIsPropagatedToWebSearchRequest() {
        template.sendBody("direct:search-false", "apache camel");

        WebSearchRequest request = capturedRequest.get();
        assertNotNull(request);
        assertFalse(request.safeSearch(), "safeSearch=false must be propagated to the WebSearchRequest");
        assertEquals("apache camel", request.searchTerms());
    }

    @Test
    void safeSearchTrueIsPropagatedToWebSearchRequest() {
        template.sendBody("direct:search-true", "apache camel");

        WebSearchRequest request = capturedRequest.get();
        assertNotNull(request);
        assertTrue(request.safeSearch(), "safeSearch=true must be propagated to the WebSearchRequest");
    }

    @Test
    void safeSearchIsPropagatedAlongsideOtherRequestParameters() {
        template.sendBody("direct:search-with-params", "apache camel");

        WebSearchRequest request = capturedRequest.get();
        assertNotNull(request);
        assertFalse(request.safeSearch());
        assertEquals(3, request.maxResults());
        assertEquals("en", request.language());
        assertEquals("US", request.geoLocation());
    }
}
