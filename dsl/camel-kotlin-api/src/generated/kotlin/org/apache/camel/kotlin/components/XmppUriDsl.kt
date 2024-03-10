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
 * Send and receive messages to/from an XMPP chat server.
 */
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

  /**
   * Hostname for the chat server
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$participant")
  }

  /**
   * Port number for the chat server
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$participant")
  }

  /**
   * Port number for the chat server
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$participant")
  }

  /**
   * JID (Jabber ID) of person to receive messages. room parameter has precedence over participant.
   */
  public fun participant(participant: String) {
    this.participant = participant
    it.url("$host:$port/$participant")
  }

  /**
   * Whether to login the user.
   */
  public fun login(login: String) {
    it.property("login", login)
  }

  /**
   * Whether to login the user.
   */
  public fun login(login: Boolean) {
    it.property("login", login.toString())
  }

  /**
   * Use nickname when joining room. If room is specified and nickname is not, user will be used for
   * the nickname.
   */
  public fun nickname(nickname: String) {
    it.property("nickname", nickname)
  }

  /**
   * Accept pubsub packets on input, default is false
   */
  public fun pubsub(pubsub: String) {
    it.property("pubsub", pubsub)
  }

  /**
   * Accept pubsub packets on input, default is false
   */
  public fun pubsub(pubsub: Boolean) {
    it.property("pubsub", pubsub.toString())
  }

  /**
   * If this option is specified, the component will connect to MUC (Multi User Chat). Usually, the
   * domain name for MUC is different from the login domain. For example, if you are supermanjabber.org
   * and want to join the krypton room, then the room URL is kryptonconference.jabber.org. Note the
   * conference part. It is not a requirement to provide the full room JID. If the room parameter does
   * not contain the symbol, the domain part will be discovered and added by Camel
   */
  public fun room(room: String) {
    it.property("room", room)
  }

  /**
   * The name of the service you are connecting to. For Google Talk, this would be gmail.com.
   */
  public fun serviceName(serviceName: String) {
    it.property("serviceName", serviceName)
  }

  /**
   * Specifies whether to test the connection on startup. This is used to ensure that the XMPP
   * client has a valid connection to the XMPP server when the route starts. Camel throws an exception
   * on startup if a connection cannot be established. When this option is set to false, Camel will
   * attempt to establish a lazy connection when needed by a producer, and will poll for a consumer
   * connection until the connection is established. Default is true.
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  /**
   * Specifies whether to test the connection on startup. This is used to ensure that the XMPP
   * client has a valid connection to the XMPP server when the route starts. Camel throws an exception
   * on startup if a connection cannot be established. When this option is set to false, Camel will
   * attempt to establish a lazy connection when needed by a producer, and will poll for a consumer
   * connection until the connection is established. Default is true.
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  /**
   * If true, an attempt to create an account will be made. Default is false.
   */
  public fun createAccount(createAccount: String) {
    it.property("createAccount", createAccount)
  }

  /**
   * If true, an attempt to create an account will be made. Default is false.
   */
  public fun createAccount(createAccount: Boolean) {
    it.property("createAccount", createAccount.toString())
  }

  /**
   * XMPP resource. The default is Camel.
   */
  public fun resource(resource: String) {
    it.property("resource", resource)
  }

  /**
   * The amount of time in seconds between polls (in seconds) to verify the health of the XMPP
   * connection, or between attempts to establish an initial consumer connection. Camel will try to
   * re-establish a connection if it has become inactive. Default is 10 seconds.
   */
  public fun connectionPollDelay(connectionPollDelay: String) {
    it.property("connectionPollDelay", connectionPollDelay)
  }

  /**
   * The amount of time in seconds between polls (in seconds) to verify the health of the XMPP
   * connection, or between attempts to establish an initial consumer connection. Camel will try to
   * re-establish a connection if it has become inactive. Default is 10 seconds.
   */
  public fun connectionPollDelay(connectionPollDelay: Int) {
    it.property("connectionPollDelay", connectionPollDelay.toString())
  }

  /**
   * Set a doc header on the IN message containing a Document form of the incoming packet; default
   * is true if presence or pubsub are true, otherwise false
   */
  public fun doc(doc: String) {
    it.property("doc", doc)
  }

  /**
   * Set a doc header on the IN message containing a Document form of the incoming packet; default
   * is true if presence or pubsub are true, otherwise false
   */
  public fun doc(doc: Boolean) {
    it.property("doc", doc.toString())
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
   * To use an existing connection configuration. Currently
   * org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration is only supported (XMPP over TCP).
   */
  public fun connectionConfig(connectionConfig: String) {
    it.property("connectionConfig", connectionConfig)
  }

  /**
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * Password for login
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Password for room
   */
  public fun roomPassword(roomPassword: String) {
    it.property("roomPassword", roomPassword)
  }

  /**
   * User name (without server name). If not specified, anonymous login will be attempted.
   */
  public fun user(user: String) {
    it.property("user", user)
  }
}
