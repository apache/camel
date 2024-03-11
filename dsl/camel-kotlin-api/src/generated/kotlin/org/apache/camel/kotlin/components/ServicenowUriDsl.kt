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
 * Interact with ServiceNow via its REST API.
 */
public fun UriDsl.servicenow(i: ServicenowUriDsl.() -> Unit) {
  ServicenowUriDsl(this).apply(i)
}

@CamelDslMarker
public class ServicenowUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("servicenow")
  }

  private var instanceName: String = ""

  /**
   * The ServiceNow instance name
   */
  public fun instanceName(instanceName: String) {
    this.instanceName = instanceName
    it.url("$instanceName")
  }

  /**
   * Set this parameter to true to return only scorecards where the indicator Display field is
   * selected. Set this parameter to all to return scorecards with any Display field value. This
   * parameter is true by default.
   */
  public fun display(display: String) {
    it.property("display", display)
  }

  /**
   * Return the display value (true), actual value (false), or both (all) for reference fields
   * (default: false)
   */
  public fun displayValue(displayValue: String) {
    it.property("displayValue", displayValue)
  }

  /**
   * True to exclude Table API links for reference fields (default: false)
   */
  public fun excludeReferenceLink(excludeReferenceLink: String) {
    it.property("excludeReferenceLink", excludeReferenceLink)
  }

  /**
   * True to exclude Table API links for reference fields (default: false)
   */
  public fun excludeReferenceLink(excludeReferenceLink: Boolean) {
    it.property("excludeReferenceLink", excludeReferenceLink.toString())
  }

  /**
   * Set this parameter to true to return only scorecards that are favorites of the querying user.
   */
  public fun favorites(favorites: String) {
    it.property("favorites", favorites)
  }

  /**
   * Set this parameter to true to return only scorecards that are favorites of the querying user.
   */
  public fun favorites(favorites: Boolean) {
    it.property("favorites", favorites.toString())
  }

  /**
   * Set this parameter to true to always return all available aggregates for an indicator,
   * including when an aggregate has already been applied. If a value is not specified, this parameter
   * defaults to false and returns no aggregates.
   */
  public fun includeAggregates(includeAggregates: String) {
    it.property("includeAggregates", includeAggregates)
  }

  /**
   * Set this parameter to true to always return all available aggregates for an indicator,
   * including when an aggregate has already been applied. If a value is not specified, this parameter
   * defaults to false and returns no aggregates.
   */
  public fun includeAggregates(includeAggregates: Boolean) {
    it.property("includeAggregates", includeAggregates.toString())
  }

  /**
   * Set this parameter to true to return all available aggregates for an indicator when no
   * aggregate has been applied. If a value is not specified, this parameter defaults to false and
   * returns no aggregates.
   */
  public fun includeAvailableAggregates(includeAvailableAggregates: String) {
    it.property("includeAvailableAggregates", includeAvailableAggregates)
  }

  /**
   * Set this parameter to true to return all available aggregates for an indicator when no
   * aggregate has been applied. If a value is not specified, this parameter defaults to false and
   * returns no aggregates.
   */
  public fun includeAvailableAggregates(includeAvailableAggregates: Boolean) {
    it.property("includeAvailableAggregates", includeAvailableAggregates.toString())
  }

  /**
   * Set this parameter to true to return all available breakdowns for an indicator. If a value is
   * not specified, this parameter defaults to false and returns no breakdowns.
   */
  public fun includeAvailableBreakdowns(includeAvailableBreakdowns: String) {
    it.property("includeAvailableBreakdowns", includeAvailableBreakdowns)
  }

  /**
   * Set this parameter to true to return all available breakdowns for an indicator. If a value is
   * not specified, this parameter defaults to false and returns no breakdowns.
   */
  public fun includeAvailableBreakdowns(includeAvailableBreakdowns: Boolean) {
    it.property("includeAvailableBreakdowns", includeAvailableBreakdowns.toString())
  }

  /**
   * Set this parameter to true to return all notes associated with the score. The note element
   * contains the note text as well as the author and timestamp when the note was added.
   */
  public fun includeScoreNotes(includeScoreNotes: String) {
    it.property("includeScoreNotes", includeScoreNotes)
  }

  /**
   * Set this parameter to true to return all notes associated with the score. The note element
   * contains the note text as well as the author and timestamp when the note was added.
   */
  public fun includeScoreNotes(includeScoreNotes: Boolean) {
    it.property("includeScoreNotes", includeScoreNotes.toString())
  }

  /**
   * Set this parameter to true to return all scores for a scorecard. If a value is not specified,
   * this parameter defaults to false and returns only the most recent score value.
   */
  public fun includeScores(includeScores: String) {
    it.property("includeScores", includeScores)
  }

  /**
   * Set this parameter to true to return all scores for a scorecard. If a value is not specified,
   * this parameter defaults to false and returns only the most recent score value.
   */
  public fun includeScores(includeScores: Boolean) {
    it.property("includeScores", includeScores.toString())
  }

  /**
   * True to set raw value of input fields (default: false)
   */
  public fun inputDisplayValue(inputDisplayValue: String) {
    it.property("inputDisplayValue", inputDisplayValue)
  }

  /**
   * True to set raw value of input fields (default: false)
   */
  public fun inputDisplayValue(inputDisplayValue: Boolean) {
    it.property("inputDisplayValue", inputDisplayValue.toString())
  }

  /**
   * Set this parameter to true to return only scorecards for key indicators.
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * Set this parameter to true to return only scorecards for key indicators.
   */
  public fun key(key: Boolean) {
    it.property("key", key.toString())
  }

  /**
   * Defines both request and response models
   */
  public fun models(models: String) {
    it.property("models", models)
  }

  /**
   * Enter the maximum number of scorecards each query can return. By default this value is 10, and
   * the maximum is 100.
   */
  public fun perPage(perPage: String) {
    it.property("perPage", perPage)
  }

  /**
   * Enter the maximum number of scorecards each query can return. By default this value is 10, and
   * the maximum is 100.
   */
  public fun perPage(perPage: Int) {
    it.property("perPage", perPage.toString())
  }

  /**
   * The ServiceNow release to target, default to Helsinki See https://docs.servicenow.com
   */
  public fun release(release: String) {
    it.property("release", release)
  }

  /**
   * Defines the request model
   */
  public fun requestModels(requestModels: String) {
    it.property("requestModels", requestModels)
  }

  /**
   * The default resource, can be overridden by header CamelServiceNowResource
   */
  public fun resource(resource: String) {
    it.property("resource", resource)
  }

  /**
   * Defines the response model
   */
  public fun responseModels(responseModels: String) {
    it.property("responseModels", responseModels)
  }

  /**
   * Specify the value to use when sorting results. By default, queries sort records by value.
   */
  public fun sortBy(sortBy: String) {
    it.property("sortBy", sortBy)
  }

  /**
   * Specify the sort direction, ascending or descending. By default, queries sort records in
   * descending order. Use sysparm_sortdir=asc to sort in ascending order.
   */
  public fun sortDir(sortDir: String) {
    it.property("sortDir", sortDir)
  }

  /**
   * True to suppress auto generation of system fields (default: false)
   */
  public fun suppressAutoSysField(suppressAutoSysField: String) {
    it.property("suppressAutoSysField", suppressAutoSysField)
  }

  /**
   * True to suppress auto generation of system fields (default: false)
   */
  public fun suppressAutoSysField(suppressAutoSysField: Boolean) {
    it.property("suppressAutoSysField", suppressAutoSysField.toString())
  }

  /**
   * Set this value to true to remove the Link header from the response. The Link header allows you
   * to request additional pages of data when the number of records matching your query exceeds the
   * query limit
   */
  public fun suppressPaginationHeader(suppressPaginationHeader: String) {
    it.property("suppressPaginationHeader", suppressPaginationHeader)
  }

  /**
   * Set this value to true to remove the Link header from the response. The Link header allows you
   * to request additional pages of data when the number of records matching your query exceeds the
   * query limit
   */
  public fun suppressPaginationHeader(suppressPaginationHeader: Boolean) {
    it.property("suppressPaginationHeader", suppressPaginationHeader.toString())
  }

  /**
   * The default table, can be overridden by header CamelServiceNowTable
   */
  public fun table(table: String) {
    it.property("table", table)
  }

  /**
   * Set this parameter to true to return only scorecards that have a target.
   */
  public fun target(target: String) {
    it.property("target", target)
  }

  /**
   * Set this parameter to true to return only scorecards that have a target.
   */
  public fun target(target: Boolean) {
    it.property("target", target.toString())
  }

  /**
   * Gets only those categories whose parent is a catalog.
   */
  public fun topLevelOnly(topLevelOnly: String) {
    it.property("topLevelOnly", topLevelOnly)
  }

  /**
   * Gets only those categories whose parent is a catalog.
   */
  public fun topLevelOnly(topLevelOnly: Boolean) {
    it.property("topLevelOnly", topLevelOnly.toString())
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
   * The ServiceNow REST API version, default latest
   */
  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  /**
   * The date format used for Json serialization/deserialization
   */
  public fun dateFormat(dateFormat: String) {
    it.property("dateFormat", dateFormat)
  }

  /**
   * The date-time format used for Json serialization/deserialization
   */
  public fun dateTimeFormat(dateTimeFormat: String) {
    it.property("dateTimeFormat", dateTimeFormat)
  }

  /**
   * To configure http-client
   */
  public fun httpClientPolicy(httpClientPolicy: String) {
    it.property("httpClientPolicy", httpClientPolicy)
  }

  /**
   * Sets Jackson's ObjectMapper to use for request/reply
   */
  public fun mapper(mapper: String) {
    it.property("mapper", mapper)
  }

  /**
   * To configure proxy authentication
   */
  public fun proxyAuthorizationPolicy(proxyAuthorizationPolicy: String) {
    it.property("proxyAuthorizationPolicy", proxyAuthorizationPolicy)
  }

  /**
   * Set this parameter to true to retrieve the target record when using import set api. The import
   * set result is then replaced by the target record
   */
  public fun retrieveTargetRecordOnImport(retrieveTargetRecordOnImport: String) {
    it.property("retrieveTargetRecordOnImport", retrieveTargetRecordOnImport)
  }

  /**
   * Set this parameter to true to retrieve the target record when using import set api. The import
   * set result is then replaced by the target record
   */
  public fun retrieveTargetRecordOnImport(retrieveTargetRecordOnImport: Boolean) {
    it.property("retrieveTargetRecordOnImport", retrieveTargetRecordOnImport.toString())
  }

  /**
   * The time format used for Json serialization/deserialization
   */
  public fun timeFormat(timeFormat: String) {
    it.property("timeFormat", timeFormat)
  }

  /**
   * The proxy host name
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * The proxy port number
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * The proxy port number
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * The ServiceNow REST API url
   */
  public fun apiUrl(apiUrl: String) {
    it.property("apiUrl", apiUrl)
  }

  /**
   * OAuth2 ClientID
   */
  public fun oauthClientId(oauthClientId: String) {
    it.property("oauthClientId", oauthClientId)
  }

  /**
   * OAuth2 ClientSecret
   */
  public fun oauthClientSecret(oauthClientSecret: String) {
    it.property("oauthClientSecret", oauthClientSecret)
  }

  /**
   * OAuth token Url
   */
  public fun oauthTokenUrl(oauthTokenUrl: String) {
    it.property("oauthTokenUrl", oauthTokenUrl)
  }

  /**
   * ServiceNow account password, MUST be provided
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Password for proxy authentication
   */
  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  /**
   * Username for proxy authentication
   */
  public fun proxyUserName(proxyUserName: String) {
    it.property("proxyUserName", proxyUserName)
  }

  /**
   * To configure security using SSLContextParameters. See
   * http://camel.apache.org/camel-configuration-utilities.html
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * ServiceNow user account name, MUST be provided
   */
  public fun userName(userName: String) {
    it.property("userName", userName)
  }
}
