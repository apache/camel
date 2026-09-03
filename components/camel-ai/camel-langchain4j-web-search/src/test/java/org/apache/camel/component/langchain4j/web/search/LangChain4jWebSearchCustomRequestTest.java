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

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LangChain4jWebSearchCustomRequestTest extends CamelTestSupport {

    private final WebSearchEngine engine = request -> WebSearchResults.from(
            WebSearchInformationResult.from(1L),
            List.of(WebSearchOrganicResult.from(
                    "Title", URI.create("https://example.com"), "snippet", "content")));

    // A custom (advanced) WebSearchRequest that deliberately does not set maxResults, so maxResults() is null.
    private final WebSearchRequest customRequest = WebSearchRequest.builder().searchTerms("apache camel").build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("engine", engine);
        context.getRegistry().bind("customRequest", customRequest);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:advanced")
                        .to("langchain4j-web-search:test?webSearchEngine=#engine&webSearchRequest=#customRequest");
            }
        };
    }

    @Test
    void customRequestWithoutMaxResultsDoesNotThrow() {
        Exchange result = template.request("direct:advanced", e -> e.getIn().setBody("apache camel"));

        // Before the fix this NPE'd unboxing a null Integer at `maxResults == 1`.
        assertNull(result.getException());
        assertNotNull(result.getMessage().getBody());
    }
}
