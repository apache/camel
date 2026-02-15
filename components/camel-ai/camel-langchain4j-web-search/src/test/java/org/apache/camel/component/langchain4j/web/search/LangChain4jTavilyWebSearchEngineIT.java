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

import java.util.List;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Get an API key at https://app.tavily.com/
@EnabledIfEnvironmentVariable(named = "TAVILY_API_KEY", matches = ".*")
public class LangChain4jTavilyWebSearchEngineIT extends CamelTestSupport {
    public static final String WEB_SEARCH_URI = "langchain4j-web-search:tavilyTest";

    @BindToRegistry("web-search-engine")
    WebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
            .apiKey(System.getenv("TAVILY_API_KEY"))
            .includeRawContent(true)
            .build();

    @BindToRegistry("web-search-request")
    WebSearchRequest request = WebSearchRequest.builder()
            .searchTerms("Who won the European Cup in 2024?")
            .maxResults(2)
            .build();

    @Test
    void testSimpleSearch() {
        Exchange result = fluentTemplate.to(WEB_SEARCH_URI)
                .withBody("Who won the European Cup in 2024?")
                .request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");

        String organicResult = result.getIn().getBody(String.class);
        assertNotNull(organicResult, "For this web search a content value is expected as a String.");
    }

    @Test
    void testSearchWithParams() {
        String uri = String.format("%s?maxResults=2&resultType=%s", WEB_SEARCH_URI,
                LangChain4jWebSearchResultType.LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT);

        Exchange result = fluentTemplate.to(uri)
                .withBody("Who won the European Cup in 2024?")
                .request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");

        List<WebSearchOrganicResult> listResult = result.getIn().getBody(List.class);
        assertNotNull(listResult, "The list results from the Tavily Search Engine shouldn't be null.");
        assertNotEquals(0, listResult.size(), "The list results from the Tavily Search Engine shouldn't be empty.");
        assertNotNull(listResult.get(0).content(), "The first result from the Tavily Search Engine should contain content.");

    }

    @Test
    void advancedRequestTest() {
        List<String> response = template.requestBody(WEB_SEARCH_URI, null, List.class);
        assertNotNull(response, "An Exchange is expected.");
        assertNotEquals(0, response.size(), "The list results from the Tavily Search Engine shouldn't be empty.");
    }

}
