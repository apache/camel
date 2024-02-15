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

  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  public fun bucket(bucket: String) {
    it.property("bucket", bucket)
  }

  public fun database(database: String) {
    it.property("database", database)
  }

  public fun readPreference(readPreference: String) {
    it.property("readPreference", readPreference)
  }

  public fun writeConcern(writeConcern: String) {
    it.property("writeConcern", writeConcern)
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun fileAttributeName(fileAttributeName: String) {
    it.property("fileAttributeName", fileAttributeName)
  }

  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  public fun persistentTSCollection(persistentTSCollection: String) {
    it.property("persistentTSCollection", persistentTSCollection)
  }

  public fun persistentTSObject(persistentTSObject: String) {
    it.property("persistentTSObject", persistentTSObject)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun queryStrategy(queryStrategy: String) {
    it.property("queryStrategy", queryStrategy)
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

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
