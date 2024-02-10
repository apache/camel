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

public fun UriDsl.xmpp(i: XmppUriDsl.() -> Unit) {
  XmppUriDsl(this).apply(i)
}

@CamelDslMarker
public class XmppUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("xmpp")
  }

  private var host: String = ""

  private var port: String = ""

  private var participant: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$participant")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$participant")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$participant")
  }

  public fun participant(participant: String) {
    this.participant = participant
    it.url("$host:$port/$participant")
  }

  public fun login(login: String) {
    it.property("login", login)
  }

  public fun login(login: Boolean) {
    it.property("login", login.toString())
  }

  public fun nickname(nickname: String) {
    it.property("nickname", nickname)
  }

  public fun pubsub(pubsub: String) {
    it.property("pubsub", pubsub)
  }

  public fun pubsub(pubsub: Boolean) {
    it.property("pubsub", pubsub.toString())
  }

  public fun room(room: String) {
    it.property("room", room)
  }

  public fun serviceName(serviceName: String) {
    it.property("serviceName", serviceName)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  public fun createAccount(createAccount: String) {
    it.property("createAccount", createAccount)
  }

  public fun createAccount(createAccount: Boolean) {
    it.property("createAccount", createAccount.toString())
  }

  public fun resource(resource: String) {
    it.property("resource", resource)
  }

  public fun connectionPollDelay(connectionPollDelay: String) {
    it.property("connectionPollDelay", connectionPollDelay)
  }

  public fun connectionPollDelay(connectionPollDelay: Int) {
    it.property("connectionPollDelay", connectionPollDelay.toString())
  }

  public fun doc(doc: String) {
    it.property("doc", doc)
  }

  public fun doc(doc: Boolean) {
    it.property("doc", doc.toString())
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

  public fun connectionConfig(connectionConfig: String) {
    it.property("connectionConfig", connectionConfig)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun roomPassword(roomPassword: String) {
    it.property("roomPassword", roomPassword)
  }

  public fun user(user: String) {
    it.property("user", user)
  }
}
