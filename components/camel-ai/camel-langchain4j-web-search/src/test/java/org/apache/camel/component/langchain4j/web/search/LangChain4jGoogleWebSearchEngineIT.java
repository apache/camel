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
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.google.customsearch.GoogleCustomWebSearchEngine;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Instruction to generate one https://developers.google.com/custom-search/v1/overview
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
// Instructions to create a Google search engine https://developers.google.com/custom-search/docs/tutorial/creatingcse
@EnabledIfEnvironmentVariable(named = "GOOGLE_SEARCH_ENGINE_ID", matches = ".*")
public class LangChain4jGoogleWebSearchEngineIT extends CamelTestSupport {
    public static final String WEB_SEARCH_URI = "langchain4j-web-search:googleTest";

    @BindToRegistry("web-search-engine")
    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
            .apiKey(System.getenv("GOOGLE_API_KEY"))
            .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @BindToRegistry("web-search-request")
    WebSearchRequest request = WebSearchRequest.builder()
            .searchTerms("Who won the European Cup in 2024?")
            .maxResults(2)
            .build();

    @Test
    void testSimpleSearch() {
        String uri = String.format("%s?resultType=%s", WEB_SEARCH_URI, LangChain4jWebSearchResultType.SNIPPET);
        Exchange result = fluentTemplate.to(uri)
                .withBody("Who won the European Cup in 2024?")
                .request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");

        String organicResult = result.getIn().getBody(String.class);
        assertNotNull(organicResult, "For this web search a snippet value is expected as a String.");
    }

    @Test
    void testSearchWithParams() {
        String uri = String.format("%s?maxResults=2&language=fr&resultType=%s", WEB_SEARCH_URI,
                LangChain4jWebSearchResultType.SNIPPET);

        Exchange result = fluentTemplate.to(uri)
                .withBody("Qui a gagné la coupe d'Europe en 2024?")
                .request(Exchange.class);

        assertNotNull(result, "An Exchange is expected.");

        List<String> listResult = result.getIn().getBody(List.class);
        assertNotNull(listResult, "The list results from the Google Search Engine shouldn't be null.");
        assertNotEquals(0, listResult.size(),
                "The list results from the Google Search Engine shouldn't be empty. It's the value of the snippet as a Strng");
    }

    @Test
    void advancedRequestTest() {
        List<String> response = template.requestBody(WEB_SEARCH_URI, null, List.class);
        assertNotNull(response, "An Exchange is expected.");
        assertNotEquals(0, response.size(), "The list results from the Google Search Engine shouldn't be empty.");
    }
}
