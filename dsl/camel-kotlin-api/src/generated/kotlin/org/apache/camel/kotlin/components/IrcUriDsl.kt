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
 * Send and receive messages to/from and IRC chat.
 */
public fun UriDsl.irc(i: IrcUriDsl.() -> Unit) {
  IrcUriDsl(this).apply(i)
}

@CamelDslMarker
public class IrcUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("irc")
  }

  private var hostname: String = ""

  private var port: String = ""

  /**
   * Hostname for the IRC chat server
   */
  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$hostname:$port")
  }

  /**
   * Port number for the IRC chat server. If no port is configured then a default port of either
   * 6667, 6668 or 6669 is used.
   */
  public fun port(port: String) {
    this.port = port
    it.url("$hostname:$port")
  }

  /**
   * Port number for the IRC chat server. If no port is configured then a default port of either
   * 6667, 6668 or 6669 is used.
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$hostname:$port")
  }

  /**
   * Whether to auto re-join when being kicked
   */
  public fun autoRejoin(autoRejoin: String) {
    it.property("autoRejoin", autoRejoin)
  }

  /**
   * Whether to auto re-join when being kicked
   */
  public fun autoRejoin(autoRejoin: Boolean) {
    it.property("autoRejoin", autoRejoin.toString())
  }

  /**
   * Comma separated list of IRC channels.
   */
  public fun channels(channels: String) {
    it.property("channels", channels)
  }

  /**
   * Delay in milliseconds before sending commands after the connection is established.
   */
  public fun commandTimeout(commandTimeout: String) {
    it.property("commandTimeout", commandTimeout)
  }

  /**
   * Delay in milliseconds before sending commands after the connection is established.
   */
  public fun commandTimeout(commandTimeout: Int) {
    it.property("commandTimeout", commandTimeout.toString())
  }

  /**
   * Comma separated list of keys for channels.
   */
  public fun keys(keys: String) {
    it.property("keys", keys)
  }

  /**
   * Sends NAMES command to channel after joining it. onReply has to be true in order to process the
   * result which will have the header value irc.num = '353'.
   */
  public fun namesOnJoin(namesOnJoin: String) {
    it.property("namesOnJoin", namesOnJoin)
  }

  /**
   * Sends NAMES command to channel after joining it. onReply has to be true in order to process the
   * result which will have the header value irc.num = '353'.
   */
  public fun namesOnJoin(namesOnJoin: Boolean) {
    it.property("namesOnJoin", namesOnJoin.toString())
  }

  /**
   * The nickname used in chat.
   */
  public fun nickname(nickname: String) {
    it.property("nickname", nickname)
  }

  /**
   * Use persistent messages.
   */
  public fun persistent(persistent: String) {
    it.property("persistent", persistent)
  }

  /**
   * Use persistent messages.
   */
  public fun persistent(persistent: Boolean) {
    it.property("persistent", persistent.toString())
  }

  /**
   * The IRC user's actual name.
   */
  public fun realname(realname: String) {
    it.property("realname", realname)
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
   * Whether or not the server supports color codes.
   */
  public fun colors(colors: String) {
    it.property("colors", colors)
  }

  /**
   * Whether or not the server supports color codes.
   */
  public fun colors(colors: Boolean) {
    it.property("colors", colors.toString())
  }

  /**
   * Handle user join events.
   */
  public fun onJoin(onJoin: String) {
    it.property("onJoin", onJoin)
  }

  /**
   * Handle user join events.
   */
  public fun onJoin(onJoin: Boolean) {
    it.property("onJoin", onJoin.toString())
  }

  /**
   * Handle kick events.
   */
  public fun onKick(onKick: String) {
    it.property("onKick", onKick)
  }

  /**
   * Handle kick events.
   */
  public fun onKick(onKick: Boolean) {
    it.property("onKick", onKick.toString())
  }

  /**
   * Handle mode change events.
   */
  public fun onMode(onMode: String) {
    it.property("onMode", onMode)
  }

  /**
   * Handle mode change events.
   */
  public fun onMode(onMode: Boolean) {
    it.property("onMode", onMode.toString())
  }

  /**
   * Handle nickname change events.
   */
  public fun onNick(onNick: String) {
    it.property("onNick", onNick)
  }

  /**
   * Handle nickname change events.
   */
  public fun onNick(onNick: Boolean) {
    it.property("onNick", onNick.toString())
  }

  /**
   * Handle user part events.
   */
  public fun onPart(onPart: String) {
    it.property("onPart", onPart)
  }

  /**
   * Handle user part events.
   */
  public fun onPart(onPart: Boolean) {
    it.property("onPart", onPart.toString())
  }

  /**
   * Handle private message events.
   */
  public fun onPrivmsg(onPrivmsg: String) {
    it.property("onPrivmsg", onPrivmsg)
  }

  /**
   * Handle private message events.
   */
  public fun onPrivmsg(onPrivmsg: Boolean) {
    it.property("onPrivmsg", onPrivmsg.toString())
  }

  /**
   * Handle user quit events.
   */
  public fun onQuit(onQuit: String) {
    it.property("onQuit", onQuit)
  }

  /**
   * Handle user quit events.
   */
  public fun onQuit(onQuit: Boolean) {
    it.property("onQuit", onQuit.toString())
  }

  /**
   * Whether or not to handle general responses to commands or informational messages.
   */
  public fun onReply(onReply: String) {
    it.property("onReply", onReply)
  }

  /**
   * Whether or not to handle general responses to commands or informational messages.
   */
  public fun onReply(onReply: Boolean) {
    it.property("onReply", onReply.toString())
  }

  /**
   * Handle topic change events.
   */
  public fun onTopic(onTopic: String) {
    it.property("onTopic", onTopic)
  }

  /**
   * Handle topic change events.
   */
  public fun onTopic(onTopic: Boolean) {
    it.property("onTopic", onTopic.toString())
  }

  /**
   * Your IRC server nickname password.
   */
  public fun nickPassword(nickPassword: String) {
    it.property("nickPassword", nickPassword)
  }

  /**
   * The IRC server password.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Used for configuring security using SSL. Reference to a
   * org.apache.camel.support.jsse.SSLContextParameters in the Registry. This reference overrides any
   * configured SSLContextParameters at the component level. Note that this setting overrides the
   * trustManager option.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * The trust manager used to verify the SSL server's certificate.
   */
  public fun trustManager(trustManager: String) {
    it.property("trustManager", trustManager)
  }

  /**
   * The IRC server user name.
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
