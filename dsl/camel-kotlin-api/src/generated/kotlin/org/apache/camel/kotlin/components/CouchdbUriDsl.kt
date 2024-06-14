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
 * Consume changesets for inserts, updates and deletes in a CouchDB database, as well as get, save,
 * update and delete documents from a CouchDB database.
 */
public fun UriDsl.couchdb(i: CouchdbUriDsl.() -> Unit) {
  CouchdbUriDsl(this).apply(i)
}

@CamelDslMarker
public class CouchdbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("couchdb")
  }

  private var protocol: String = ""

  private var hostname: String = ""

  private var port: String = ""

  private var database: String = ""

  /**
   * The protocol to use for communicating with the database.
   */
  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol:$hostname:$port/$database")
  }

  /**
   * Hostname of the running couchdb instance
   */
  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$protocol:$hostname:$port/$database")
  }

  /**
   * Port number for the running couchdb instance
   */
  public fun port(port: String) {
    this.port = port
    it.url("$protocol:$hostname:$port/$database")
  }

  /**
   * Port number for the running couchdb instance
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol:$hostname:$port/$database")
  }

  /**
   * Name of the database to use
   */
  public fun database(database: String) {
    this.database = database
    it.url("$protocol:$hostname:$port/$database")
  }

  /**
   * Creates the database if it does not already exist
   */
  public fun createDatabase(createDatabase: String) {
    it.property("createDatabase", createDatabase)
  }

  /**
   * Creates the database if it does not already exist
   */
  public fun createDatabase(createDatabase: Boolean) {
    it.property("createDatabase", createDatabase.toString())
  }

  /**
   * Document deletes are published as events
   */
  public fun deletes(deletes: String) {
    it.property("deletes", deletes)
  }

  /**
   * Document deletes are published as events
   */
  public fun deletes(deletes: Boolean) {
    it.property("deletes", deletes.toString())
  }

  /**
   * How often to send an empty message to keep socket alive in millis
   */
  public fun heartbeat(heartbeat: String) {
    it.property("heartbeat", heartbeat)
  }

  /**
   * Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number
   * of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative
   * number to set it as unlimited.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number
   * of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative
   * number to set it as unlimited.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * Specifies how many revisions are returned in the changes array. The default, main_only, will
   * only return the current winning revision; all_docs will return all leaf revisions (including
   * conflicts and deleted former conflicts.)
   */
  public fun style(style: String) {
    it.property("style", style)
  }

  /**
   * Document inserts/updates are published as events
   */
  public fun updates(updates: String) {
    it.property("updates", updates)
  }

  /**
   * Document inserts/updates are published as events
   */
  public fun updates(updates: Boolean) {
    it.property("updates", updates.toString())
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
   * Password for authenticated databases
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Username in case of authenticated databases
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
