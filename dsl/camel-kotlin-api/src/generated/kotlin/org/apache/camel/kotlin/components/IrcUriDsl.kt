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

  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$hostname:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$hostname:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$hostname:$port")
  }

  public fun autoRejoin(autoRejoin: String) {
    it.property("autoRejoin", autoRejoin)
  }

  public fun autoRejoin(autoRejoin: Boolean) {
    it.property("autoRejoin", autoRejoin.toString())
  }

  public fun channels(channels: String) {
    it.property("channels", channels)
  }

  public fun commandTimeout(commandTimeout: String) {
    it.property("commandTimeout", commandTimeout)
  }

  public fun commandTimeout(commandTimeout: Int) {
    it.property("commandTimeout", commandTimeout.toString())
  }

  public fun keys(keys: String) {
    it.property("keys", keys)
  }

  public fun namesOnJoin(namesOnJoin: String) {
    it.property("namesOnJoin", namesOnJoin)
  }

  public fun namesOnJoin(namesOnJoin: Boolean) {
    it.property("namesOnJoin", namesOnJoin.toString())
  }

  public fun nickname(nickname: String) {
    it.property("nickname", nickname)
  }

  public fun persistent(persistent: String) {
    it.property("persistent", persistent)
  }

  public fun persistent(persistent: Boolean) {
    it.property("persistent", persistent.toString())
  }

  public fun realname(realname: String) {
    it.property("realname", realname)
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

  public fun colors(colors: String) {
    it.property("colors", colors)
  }

  public fun colors(colors: Boolean) {
    it.property("colors", colors.toString())
  }

  public fun onJoin(onJoin: String) {
    it.property("onJoin", onJoin)
  }

  public fun onJoin(onJoin: Boolean) {
    it.property("onJoin", onJoin.toString())
  }

  public fun onKick(onKick: String) {
    it.property("onKick", onKick)
  }

  public fun onKick(onKick: Boolean) {
    it.property("onKick", onKick.toString())
  }

  public fun onMode(onMode: String) {
    it.property("onMode", onMode)
  }

  public fun onMode(onMode: Boolean) {
    it.property("onMode", onMode.toString())
  }

  public fun onNick(onNick: String) {
    it.property("onNick", onNick)
  }

  public fun onNick(onNick: Boolean) {
    it.property("onNick", onNick.toString())
  }

  public fun onPart(onPart: String) {
    it.property("onPart", onPart)
  }

  public fun onPart(onPart: Boolean) {
    it.property("onPart", onPart.toString())
  }

  public fun onPrivmsg(onPrivmsg: String) {
    it.property("onPrivmsg", onPrivmsg)
  }

  public fun onPrivmsg(onPrivmsg: Boolean) {
    it.property("onPrivmsg", onPrivmsg.toString())
  }

  public fun onQuit(onQuit: String) {
    it.property("onQuit", onQuit)
  }

  public fun onQuit(onQuit: Boolean) {
    it.property("onQuit", onQuit.toString())
  }

  public fun onReply(onReply: String) {
    it.property("onReply", onReply)
  }

  public fun onReply(onReply: Boolean) {
    it.property("onReply", onReply.toString())
  }

  public fun onTopic(onTopic: String) {
    it.property("onTopic", onTopic)
  }

  public fun onTopic(onTopic: Boolean) {
    it.property("onTopic", onTopic.toString())
  }

  public fun nickPassword(nickPassword: String) {
    it.property("nickPassword", nickPassword)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun trustManager(trustManager: String) {
    it.property("trustManager", trustManager)
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
