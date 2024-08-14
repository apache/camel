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
 * Interact with JIRA issue tracker.
 */
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

  /**
   * Operation to perform. Consumers: NewIssues, NewComments. Producers: AddIssue, AttachFile,
   * DeleteIssue, TransitionIssue, UpdateIssue, Watchers. See this class javadoc description for more
   * information.
   */
  public fun type(type: String) {
    this.type = type
    it.url("$type")
  }

  /**
   * Time in milliseconds to elapse for the next poll.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Time in milliseconds to elapse for the next poll.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  /**
   * The Jira server url, for example http://my_jira.com:8081.
   */
  public fun jiraUrl(jiraUrl: String) {
    it.property("jiraUrl", jiraUrl)
  }

  /**
   * JQL is the query language from JIRA which allows you to retrieve the data you want. For example
   * jql=project=MyProject Where MyProject is the product key in Jira. It is important to use the RAW()
   * and set the JQL inside it to prevent camel parsing it, example: RAW(project in (MYP, COM) AND
   * resolution = Unresolved)
   */
  public fun jql(jql: String) {
    it.property("jql", jql)
  }

  /**
   * Max number of issues to search for
   */
  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  /**
   * Max number of issues to search for
   */
  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  /**
   * Indicator for sending only changed fields in exchange body or issue object. By default consumer
   * sends only changed fields.
   */
  public fun sendOnlyUpdatedField(sendOnlyUpdatedField: String) {
    it.property("sendOnlyUpdatedField", sendOnlyUpdatedField)
  }

  /**
   * Indicator for sending only changed fields in exchange body or issue object. By default consumer
   * sends only changed fields.
   */
  public fun sendOnlyUpdatedField(sendOnlyUpdatedField: Boolean) {
    it.property("sendOnlyUpdatedField", sendOnlyUpdatedField.toString())
  }

  /**
   * Comma separated list of fields to watch for changes. Status,Priority are the defaults.
   */
  public fun watchedFields(watchedFields: String) {
    it.property("watchedFields", watchedFields)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
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
   * (OAuth or Personal Access Token authentication) The access token generated by the Jira server.
   */
  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  /**
   * (OAuth only) The consumer key from Jira settings.
   */
  public fun consumerKey(consumerKey: String) {
    it.property("consumerKey", consumerKey)
  }

  /**
   * (Basic authentication only) The password or the API Token to authenticate to the Jira server.
   * Use only if username basic authentication is used.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * (OAuth only) The private key generated by the client to encrypt the conversation to the server.
   */
  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  /**
   * (Basic authentication only) The username to authenticate to the Jira server. Use only if OAuth
   * is not enabled on the Jira server. Do not set the username and OAuth token parameter, if they are
   * both set, the username basic authentication takes precedence.
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * (OAuth only) The verification code from Jira generated in the first step of the authorization
   * proccess.
   */
  public fun verificationCode(verificationCode: String) {
    it.property("verificationCode", verificationCode)
  }
}
