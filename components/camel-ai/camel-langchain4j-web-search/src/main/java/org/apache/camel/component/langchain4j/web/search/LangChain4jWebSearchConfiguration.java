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
    @UriParam
    private WebSearchEngine webSearchEngine;

    @Metadata(required = true, defaultValue = "CONTENT")
    @UriParam
    private LangChain4jWebSearchResultType resultType = LangChain4jWebSearchResultType.CONTENT;

    @Metadata(required = true, defaultValue = "1")
    @UriParam
    private Integer maxResults = 1;

    @UriParam
    private String language;

    @UriParam
    private String geoLocation;

    @UriParam
    private Integer startPage;

    @UriParam
    private Integer startIndex;

    @UriParam
    private Boolean safeSearch;

    @UriParam
    @Metadata(autowired = true)
    private Map<String, Object> additionalParams;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private WebSearchRequest webSearchRequest;

    public WebSearchEngine getWebSearchEngine() {
        return webSearchEngine;
    }

    /**
     * The {@link WebSearchEngine} engine to use. This is mandatory. Use one of the implementations from Langchain4j web
     * search engines.
     */
    public void setWebSearchEngine(WebSearchEngine webSearchEngine) {
        this.webSearchEngine = webSearchEngine;
    }

    public LangChain4jWebSearchResultType getResultType() {
        return resultType;
    }

    /**
     * The {@link #resultType} is the result type of the request. Valid values are
     * LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT, CONTENT, or SNIPPET. CONTENT is the default value; it will return a list
     * of String . You can also specify to return either the Langchain4j Web Search Organic Result object (using
     * `LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT`) or snippet (using `SNIPPET`) for each result. If {@link #maxResults} is
     * equal to 1, the response will be a single object instead of a list.
     */
    public void setResultType(LangChain4jWebSearchResultType resultType) {
        this.resultType = resultType;
    }

    /**
     * The {@link #maxResults} is the expected number of results to be found if the search request were made. Each
     * search engine may have a different limit for the maximum number of results that can be returned.
     *
     * @return
     */
    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    /**
     * The {@link #language} is the desired language for search results. The expected values may vary depending on the
     * search engine.
     *
     * @return
     */
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getGeoLocation() {
        return geoLocation;
    }

    /**
     * The {@link #geoLocation} is the desired geolocation for search results. Each search engine may have a different
     * set of supported geolocations.
     */
    public void setGeoLocation(String geoLocation) {
        this.geoLocation = geoLocation;
    }

    public Integer getStartPage() {
        return startPage;
    }

    /**
     * The {@link #startPage} is the start page number for search results
     */
    public void setStartPage(Integer startPage) {
        this.startPage = startPage;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    /**
     * The {@link #startIndex} is the start index for search results, which may vary depending on the search engine.
     */
    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Boolean getSafeSearch() {
        return safeSearch;
    }

    /**
     * The {@link #safeSearch} is the safe search flag, indicating whether to enable or disable safe search.
     */
    public void setSafeSearch(Boolean safeSearch) {
        this.safeSearch = safeSearch;
    }

    public Map<String, Object> getAdditionalParams() {
        return additionalParams;
    }

    /**
     * The {@link #additionalParams} is the additional parameters for the search request are a map of key-value pairs
     * that represent additional parameters for the search request.
     */
    public void setAdditionalParams(Map<String, Object> additionalParams) {
        this.additionalParams = additionalParams;
    }

    public WebSearchRequest getWebSearchRequest() {
        return webSearchRequest;
    }

    /**
     * The {@link #webSearchRequest} is the custom WebSearchRequest - advanced
     */
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
