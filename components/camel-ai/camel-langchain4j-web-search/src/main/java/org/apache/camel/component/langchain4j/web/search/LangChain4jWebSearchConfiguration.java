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

import java.util.Map;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jWebSearchConfiguration implements Cloneable {

    @Metadata(required = true, autowired = true)
    @UriParam(description = "The dev.langchain4j.web.search.WebSearchEngine that runs the search, for example a"
                            + " TavilyWebSearchEngine or a GoogleCustomWebSearchEngine of the LangChain4j web search engine"
                            + " modules. If not set, the single instance in the registry is used, and the producer fails to"
                            + " start when there is none.")
    private WebSearchEngine webSearchEngine;

    @Metadata(required = true, defaultValue = "CONTENT")
    @UriParam(description = "What the producer returns for each result: CONTENT its content as a String, or its snippet when"
                            + " the result has no content (as some engines give none), SNIPPET its snippet, or"
                            + " LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT the dev.langchain4j.web.search.WebSearchOrganicResult."
                            + " The body is a single value when the request asks for one result, otherwise a List, and null"
                            + " when there is no result.")
    private LangChain4jWebSearchResultType resultType = LangChain4jWebSearchResultType.CONTENT;

    @Metadata(required = true, defaultValue = "1")
    @UriParam(description = "The number of results that the producer asks the search engine for. With 1 the body is a single"
                            + " result, otherwise a List. An engine can return fewer results and can have its own maximum.")
    private Integer maxResults = 1;
    @UriParam(description = "The language of the search results, in the format that the search engine expects. The Tavily"
                            + " engine ignores it.")
    private String language;
    @UriParam(description = "The geographic location of the search results, such as a country code, in the format that the"
                            + " search engine expects. The Tavily engine ignores it.")
    private String geoLocation;
    @UriParam(description = "The page number of the search results to return. If not set, LangChain4j uses 1. The Tavily"
                            + " engine ignores it.")
    private Integer startPage;
    @UriParam(description = "The index of the first search result to return, in the numbering that the search engine uses."
                            + " The Tavily engine ignores it.")
    private Integer startIndex;
    @UriParam(description = "Whether the search engine filters explicit content out of the results. If not set, LangChain4j"
                            + " uses true. The Tavily engine ignores it.")
    private Boolean safeSearch;
    @UriParam(description = "More parameters for the search engine, as a Map of names to values, such as the parameters of"
                            + " the search API that no option covers. Which names are used depends on the engine, and the"
                            + " Tavily engine ignores them.")
    private Map<String, Object> additionalParams;

    @UriParam(label = "advanced",
              description = "A dev.langchain4j.web.search.WebSearchRequest that the producer sends as it is. When set, the"
                            + " producer does not read the message body and does not use the other search options, and"
                            + " only a maxResults of 1 in this request gives a single result instead of a List.")
    private WebSearchRequest webSearchRequest;

    public WebSearchEngine getWebSearchEngine() {
        return webSearchEngine;
    }

    public void setWebSearchEngine(WebSearchEngine webSearchEngine) {
        this.webSearchEngine = webSearchEngine;
    }

    public LangChain4jWebSearchResultType getResultType() {
        return resultType;
    }

    public void setResultType(LangChain4jWebSearchResultType resultType) {
        this.resultType = resultType;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getGeoLocation() {
        return geoLocation;
    }

    public void setGeoLocation(String geoLocation) {
        this.geoLocation = geoLocation;
    }

    public Integer getStartPage() {
        return startPage;
    }

    public void setStartPage(Integer startPage) {
        this.startPage = startPage;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Boolean getSafeSearch() {
        return safeSearch;
    }

    public void setSafeSearch(Boolean safeSearch) {
        this.safeSearch = safeSearch;
    }

    public Map<String, Object> getAdditionalParams() {
        return additionalParams;
    }

    public void setAdditionalParams(Map<String, Object> additionalParams) {
        this.additionalParams = additionalParams;
    }

    public WebSearchRequest getWebSearchRequest() {
        return webSearchRequest;
    }

    public void setWebSearchRequest(WebSearchRequest webSearchRequest) {
        this.webSearchRequest = webSearchRequest;
    }

    public LangChain4jWebSearchConfiguration copy() {
        try {
            return (LangChain4jWebSearchConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
