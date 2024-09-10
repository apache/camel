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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * LangChain4j Web Search Engine
 */
public fun UriDsl.`langchain4j-web-search`(i: Langchain4jWebSearchUriDsl.() -> Unit) {
  Langchain4jWebSearchUriDsl(this).apply(i)
}

@CamelDslMarker
public class Langchain4jWebSearchUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("langchain4j-web-search")
  }

  private var searchId: String = ""

  /**
   * The id
   */
  public fun searchId(searchId: String) {
    this.searchId = searchId
    it.url("$searchId")
  }

  /**
   * The additionalParams is the additional parameters for the search request are a map of key-value
   * pairs that represent additional parameters for the search request.
   */
  public fun additionalParams(additionalParams: String) {
    it.property("additionalParams", additionalParams)
  }

  /**
   * The geoLocation is the desired geolocation for search results. Each search engine may have a
   * different set of supported geolocations.
   */
  public fun geoLocation(geoLocation: String) {
    it.property("geoLocation", geoLocation)
  }

  /**
   * The language is the desired language for search results. The expected values may vary depending
   * on the search engine.
   */
  public fun language(language: String) {
    it.property("language", language)
  }

  /**
   * The maxResults is the expected number of results to be found if the search request were made.
   * Each search engine may have a different limit for the maximum number of results that can be
   * returned.
   */
  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  /**
   * The maxResults is the expected number of results to be found if the search request were made.
   * Each search engine may have a different limit for the maximum number of results that can be
   * returned.
   */
  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  /**
   * The resultType is the result type of the request. Valid values are
   * LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT, CONTENT, or SNIPPET. CONTENT is the default value; it will
   * return a list of String . You can also specify to return either the Langchain4j Web Search Organic
   * Result object (using LANGCHAIN4J_WEB_SEARCH_ORGANIC_RESULT) or snippet (using SNIPPET) for each
   * result. If maxResults is equal to 1, the response will be a single object instead of a list.
   */
  public fun resultType(resultType: String) {
    it.property("resultType", resultType)
  }

  /**
   * The safeSearch is the safe search flag, indicating whether to enable or disable safe search.
   */
  public fun safeSearch(safeSearch: String) {
    it.property("safeSearch", safeSearch)
  }

  /**
   * The safeSearch is the safe search flag, indicating whether to enable or disable safe search.
   */
  public fun safeSearch(safeSearch: Boolean) {
    it.property("safeSearch", safeSearch.toString())
  }

  /**
   * The startIndex is the start index for search results, which may vary depending on the search
   * engine.
   */
  public fun startIndex(startIndex: String) {
    it.property("startIndex", startIndex)
  }

  /**
   * The startIndex is the start index for search results, which may vary depending on the search
   * engine.
   */
  public fun startIndex(startIndex: Int) {
    it.property("startIndex", startIndex.toString())
  }

  /**
   * The startPage is the start page number for search results
   */
  public fun startPage(startPage: String) {
    it.property("startPage", startPage)
  }

  /**
   * The startPage is the start page number for search results
   */
  public fun startPage(startPage: Int) {
    it.property("startPage", startPage.toString())
  }

  /**
   * The WebSearchEngine engine to use. This is mandatory. Use one of the implementations from
   * Langchain4j web search engines.
   */
  public fun webSearchEngine(webSearchEngine: String) {
    it.property("webSearchEngine", webSearchEngine)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * The webSearchRequest is the custom WebSearchRequest - advanced
   */
  public fun webSearchRequest(webSearchRequest: String) {
    it.property("webSearchRequest", webSearchRequest)
  }
}
