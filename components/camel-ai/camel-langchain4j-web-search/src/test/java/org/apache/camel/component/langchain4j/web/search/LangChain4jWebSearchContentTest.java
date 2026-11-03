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
import dev.langchain4j.web.search.WebSearchResults;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Engines such as Google Custom Search give a snippet but no content: the CONTENT result type then gives the snippet.
 */
public class LangChain4jWebSearchContentTest extends CamelTestSupport {

    private final WebSearchEngine engine = request -> WebSearchResults.from(
            WebSearchInformationResult.from(3L),
            List.of(WebSearchOrganicResult.from("Title", URI.create("https://example.com"), "snippet", null),
                    WebSearchOrganicResult.from("Title 2", URI.create("https://example.org"), "snippet 2", "content 2"),
                    WebSearchOrganicResult.from("Title 3", URI.create("https://example.net"), "snippet 3", " ")));

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("engine", engine);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:content")
                        .to("langchain4j-web-search:test?webSearchEngine=#engine");

                from("direct:contents")
                        .to("langchain4j-web-search:test?webSearchEngine=#engine&maxResults=3");

                from("direct:snippets")
                        .to("langchain4j-web-search:test?webSearchEngine=#engine&maxResults=3&resultType=SNIPPET");
            }
        };
    }

    @Test
    void singleResultWithoutContentGivesItsSnippet() {
        assertEquals("snippet", template.requestBody("direct:content", "apache camel"));
    }

    @Test
    void listGivesTheSnippetOfEachResultWithoutContent() {
        assertEquals(List.of("snippet", "content 2", "snippet 3"), template.requestBody("direct:contents", "apache camel"));
    }

    @Test
    void snippetResultTypeIsUnchanged() {
        assertEquals(List.of("snippet", "snippet 2", "snippet 3"),
                template.requestBody("direct:snippets", "apache camel"));
    }
}
