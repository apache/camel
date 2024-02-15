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

  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol:$hostname:$port/$database")
  }

  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$protocol:$hostname:$port/$database")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$protocol:$hostname:$port/$database")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol:$hostname:$port/$database")
  }

  public fun database(database: String) {
    this.database = database
    it.url("$protocol:$hostname:$port/$database")
  }

  public fun createDatabase(createDatabase: String) {
    it.property("createDatabase", createDatabase)
  }

  public fun createDatabase(createDatabase: Boolean) {
    it.property("createDatabase", createDatabase.toString())
  }

  public fun deletes(deletes: String) {
    it.property("deletes", deletes)
  }

  public fun deletes(deletes: Boolean) {
    it.property("deletes", deletes.toString())
  }

  public fun heartbeat(heartbeat: String) {
    it.property("heartbeat", heartbeat)
  }

  public fun style(style: String) {
    it.property("style", style)
  }

  public fun updates(updates: String) {
    it.property("updates", updates)
  }

  public fun updates(updates: Boolean) {
    it.property("updates", updates.toString())
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

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
