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

public fun UriDsl.jira(i: JiraUriDsl.() -> Unit) {
  JiraUriDsl(this).apply(i)
}

@CamelDslMarker
public class JiraUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jira")
  }

  private var type: String = ""

  public fun type(type: String) {
    this.type = type
    it.url("$type")
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  public fun jiraUrl(jiraUrl: String) {
    it.property("jiraUrl", jiraUrl)
  }

  public fun jql(jql: String) {
    it.property("jql", jql)
  }

  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  public fun sendOnlyUpdatedField(sendOnlyUpdatedField: String) {
    it.property("sendOnlyUpdatedField", sendOnlyUpdatedField)
  }

  public fun sendOnlyUpdatedField(sendOnlyUpdatedField: Boolean) {
    it.property("sendOnlyUpdatedField", sendOnlyUpdatedField.toString())
  }

  public fun watchedFields(watchedFields: String) {
    it.property("watchedFields", watchedFields)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  public fun consumerKey(consumerKey: String) {
    it.property("consumerKey", consumerKey)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun verificationCode(verificationCode: String) {
    it.property("verificationCode", verificationCode)
  }
}
