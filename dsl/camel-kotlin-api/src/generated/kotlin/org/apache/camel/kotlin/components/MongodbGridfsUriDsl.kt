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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Interact with MongoDB GridFS.
 */
public fun UriDsl.`mongodb-gridfs`(i: MongodbGridfsUriDsl.() -> Unit) {
  MongodbGridfsUriDsl(this).apply(i)
}

@CamelDslMarker
public class MongodbGridfsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mongodb-gridfs")
  }

  private var connectionBean: String = ""

  /**
   * Name of com.mongodb.client.MongoClient to use.
   */
  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  /**
   * Sets the name of the GridFS bucket within the database. Default is fs.
   */
  public fun bucket(bucket: String) {
    it.property("bucket", bucket)
  }

  /**
   * Sets the name of the MongoDB database to target
   */
  public fun database(database: String) {
    it.property("database", database)
  }

  /**
   * Sets a MongoDB ReadPreference on the Mongo connection. Read preferences set directly on the
   * connection will be overridden by this setting. The com.mongodb.ReadPreference#valueOf(String)
   * utility method is used to resolve the passed readPreference value. Some examples for the possible
   * values are nearest, primary or secondary etc.
   */
  public fun readPreference(readPreference: String) {
    it.property("readPreference", readPreference)
  }

  /**
   * Set the WriteConcern for write operations on MongoDB using the standard ones. Resolved from the
   * fields of the WriteConcern class by calling the WriteConcern#valueOf(String) method.
   */
  public fun writeConcern(writeConcern: String) {
    it.property("writeConcern", writeConcern)
  }

  /**
   * Sets the delay between polls within the Consumer. Default is 500ms
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * If the QueryType uses a FileAttribute, this sets the name of the attribute that is used.
   * Default is camel-processed.
   */
  public fun fileAttributeName(fileAttributeName: String) {
    it.property("fileAttributeName", fileAttributeName)
  }

  /**
   * Sets the initialDelay before the consumer will start polling. Default is 1000ms
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * If the QueryType uses a persistent timestamp, this sets the name of the collection within the
   * DB to store the timestamp.
   */
  public fun persistentTSCollection(persistentTSCollection: String) {
    it.property("persistentTSCollection", persistentTSCollection)
  }

  /**
   * If the QueryType uses a persistent timestamp, this is the ID of the object in the collection to
   * store the timestamp.
   */
  public fun persistentTSObject(persistentTSObject: String) {
    it.property("persistentTSObject", persistentTSObject)
  }

  /**
   * Additional query parameters (in JSON) that are used to configure the query used for finding
   * files in the GridFsConsumer
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * Sets the QueryStrategy that is used for polling for new files. Default is Timestamp
   */
  public fun queryStrategy(queryStrategy: String) {
    it.property("queryStrategy", queryStrategy)
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
   * Sets the operation this endpoint will execute against GridFs.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
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
}
