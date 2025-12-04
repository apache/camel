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
import java.util.stream.Collectors;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class LangChain4jWebSearchProducer extends DefaultProducer {

    private WebSearchEngine webSearchEngine;

    public LangChain4jWebSearchProducer(LangChain4jWebSearchEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChain4jWebSearchEndpoint getEndpoint() {
        return (LangChain4jWebSearchEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // check if there's a custom WebSearchRequest -- advanced
        WebSearchRequest webSearchRequest = getEndpoint().getConfiguration().getWebSearchRequest();

        // build a Web Search Request
        if (webSearchRequest == null) {
            final String searchTerms = exchange.getMessage().getMandatoryBody(String.class);
            webSearchRequest = WebSearchRequest.builder()
                    .searchTerms(searchTerms)
                    .maxResults(getEndpoint().getConfiguration().getMaxResults())
                    .language(getEndpoint().getConfiguration().getLanguage())
                    .geoLocation(getEndpoint().getConfiguration().getGeoLocation())
                    .startPage(getEndpoint().getConfiguration().getStartPage())
                    .startIndex(getEndpoint().getConfiguration().getStartIndex())
                    .additionalParams(getEndpoint().getConfiguration().getAdditionalParams())
                    .build();
        }

        // perform the request
        WebSearchResults webSearchResults = webSearchEngine.search(webSearchRequest);

        // exrtact the list
        List<WebSearchOrganicResult> resultList = webSearchResults.results();

        // compute the response
        computeResponse(resultList, exchange, webSearchRequest.maxResults());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.webSearchEngine = getEndpoint().getConfiguration().getWebSearchEngine();
        ObjectHelper.notNull(webSearchEngine, "webSearchEngine");
    }

    /**
     * Computes the response of the web search based on the configuration and input results.
     *
     * @param webSearchOrganicResults the list of WebSearchOrganicResult objects to process
     * @param exchange                the Apache Camel Exchange object
     * @param maxResults              maxResults
     */
    private void computeResponse(
            List<WebSearchOrganicResult> webSearchOrganicResults, Exchange exchange, Integer maxResults) {
        // Check if the input list is null or empty and handle it gracefully
        if (webSearchOrganicResults == null || webSearchOrganicResults.isEmpty()) {
            exchange.getIn().setBody(null);
            return;
        }

        // return a single object as a response
        if (maxResults == 1) {
            switch (getEndpoint().getConfiguration().getResultType()) {
                case LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT -> exchange.getIn().setBody(webSearchOrganicResults.get(0));
                case CONTENT -> exchange.getIn()
                        .setBody(webSearchOrganicResults.get(0).content());
                case SNIPPET -> exchange.getIn()
                        .setBody(webSearchOrganicResults.get(0).snippet());
            }
        } else { // return a List of Objects as a response
            switch (getEndpoint().getConfiguration().getResultType()) {
                case LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT -> exchange.getIn().setBody(webSearchOrganicResults);
                case CONTENT -> exchange.getIn()
                        .setBody(webSearchOrganicResults.stream()
                                .map(WebSearchOrganicResult::content)
                                .collect(Collectors.toList()));
                case SNIPPET -> exchange.getIn()
                        .setBody(webSearchOrganicResults.stream()
                                .map(WebSearchOrganicResult::snippet)
                                .collect(Collectors.toList()));
            }
        }
    }
}
