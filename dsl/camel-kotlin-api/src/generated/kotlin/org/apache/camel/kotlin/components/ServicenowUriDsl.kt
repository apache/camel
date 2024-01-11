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

  public fun instanceName(instanceName: String) {
    this.instanceName = instanceName
    it.url("$instanceName")
  }

  public fun display(display: String) {
    it.property("display", display)
  }

  public fun displayValue(displayValue: String) {
    it.property("displayValue", displayValue)
  }

  public fun excludeReferenceLink(excludeReferenceLink: String) {
    it.property("excludeReferenceLink", excludeReferenceLink)
  }

  public fun excludeReferenceLink(excludeReferenceLink: Boolean) {
    it.property("excludeReferenceLink", excludeReferenceLink.toString())
  }

  public fun favorites(favorites: String) {
    it.property("favorites", favorites)
  }

  public fun favorites(favorites: Boolean) {
    it.property("favorites", favorites.toString())
  }

  public fun includeAggregates(includeAggregates: String) {
    it.property("includeAggregates", includeAggregates)
  }

  public fun includeAggregates(includeAggregates: Boolean) {
    it.property("includeAggregates", includeAggregates.toString())
  }

  public fun includeAvailableAggregates(includeAvailableAggregates: String) {
    it.property("includeAvailableAggregates", includeAvailableAggregates)
  }

  public fun includeAvailableAggregates(includeAvailableAggregates: Boolean) {
    it.property("includeAvailableAggregates", includeAvailableAggregates.toString())
  }

  public fun includeAvailableBreakdowns(includeAvailableBreakdowns: String) {
    it.property("includeAvailableBreakdowns", includeAvailableBreakdowns)
  }

  public fun includeAvailableBreakdowns(includeAvailableBreakdowns: Boolean) {
    it.property("includeAvailableBreakdowns", includeAvailableBreakdowns.toString())
  }

  public fun includeScoreNotes(includeScoreNotes: String) {
    it.property("includeScoreNotes", includeScoreNotes)
  }

  public fun includeScoreNotes(includeScoreNotes: Boolean) {
    it.property("includeScoreNotes", includeScoreNotes.toString())
  }

  public fun includeScores(includeScores: String) {
    it.property("includeScores", includeScores)
  }

  public fun includeScores(includeScores: Boolean) {
    it.property("includeScores", includeScores.toString())
  }

  public fun inputDisplayValue(inputDisplayValue: String) {
    it.property("inputDisplayValue", inputDisplayValue)
  }

  public fun inputDisplayValue(inputDisplayValue: Boolean) {
    it.property("inputDisplayValue", inputDisplayValue.toString())
  }

  public fun key(key: String) {
    it.property("key", key)
  }

  public fun key(key: Boolean) {
    it.property("key", key.toString())
  }

  public fun models(models: String) {
    it.property("models", models)
  }

  public fun perPage(perPage: String) {
    it.property("perPage", perPage)
  }

  public fun perPage(perPage: Int) {
    it.property("perPage", perPage.toString())
  }

  public fun release(release: String) {
    it.property("release", release)
  }

  public fun requestModels(requestModels: String) {
    it.property("requestModels", requestModels)
  }

  public fun resource(resource: String) {
    it.property("resource", resource)
  }

  public fun responseModels(responseModels: String) {
    it.property("responseModels", responseModels)
  }

  public fun sortBy(sortBy: String) {
    it.property("sortBy", sortBy)
  }

  public fun sortDir(sortDir: String) {
    it.property("sortDir", sortDir)
  }

  public fun suppressAutoSysField(suppressAutoSysField: String) {
    it.property("suppressAutoSysField", suppressAutoSysField)
  }

  public fun suppressAutoSysField(suppressAutoSysField: Boolean) {
    it.property("suppressAutoSysField", suppressAutoSysField.toString())
  }

  public fun suppressPaginationHeader(suppressPaginationHeader: String) {
    it.property("suppressPaginationHeader", suppressPaginationHeader)
  }

  public fun suppressPaginationHeader(suppressPaginationHeader: Boolean) {
    it.property("suppressPaginationHeader", suppressPaginationHeader.toString())
  }

  public fun table(table: String) {
    it.property("table", table)
  }

  public fun target(target: String) {
    it.property("target", target)
  }

  public fun target(target: Boolean) {
    it.property("target", target.toString())
  }

  public fun topLevelOnly(topLevelOnly: String) {
    it.property("topLevelOnly", topLevelOnly)
  }

  public fun topLevelOnly(topLevelOnly: Boolean) {
    it.property("topLevelOnly", topLevelOnly.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  public fun dateFormat(dateFormat: String) {
    it.property("dateFormat", dateFormat)
  }

  public fun dateTimeFormat(dateTimeFormat: String) {
    it.property("dateTimeFormat", dateTimeFormat)
  }

  public fun httpClientPolicy(httpClientPolicy: String) {
    it.property("httpClientPolicy", httpClientPolicy)
  }

  public fun mapper(mapper: String) {
    it.property("mapper", mapper)
  }

  public fun proxyAuthorizationPolicy(proxyAuthorizationPolicy: String) {
    it.property("proxyAuthorizationPolicy", proxyAuthorizationPolicy)
  }

  public fun retrieveTargetRecordOnImport(retrieveTargetRecordOnImport: String) {
    it.property("retrieveTargetRecordOnImport", retrieveTargetRecordOnImport)
  }

  public fun retrieveTargetRecordOnImport(retrieveTargetRecordOnImport: Boolean) {
    it.property("retrieveTargetRecordOnImport", retrieveTargetRecordOnImport.toString())
  }

  public fun timeFormat(timeFormat: String) {
    it.property("timeFormat", timeFormat)
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun apiUrl(apiUrl: String) {
    it.property("apiUrl", apiUrl)
  }

  public fun oauthClientId(oauthClientId: String) {
    it.property("oauthClientId", oauthClientId)
  }

  public fun oauthClientSecret(oauthClientSecret: String) {
    it.property("oauthClientSecret", oauthClientSecret)
  }

  public fun oauthTokenUrl(oauthTokenUrl: String) {
    it.property("oauthTokenUrl", oauthTokenUrl)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  public fun proxyUserName(proxyUserName: String) {
    it.property("proxyUserName", proxyUserName)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun userName(userName: String) {
    it.property("userName", userName)
  }
}
