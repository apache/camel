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

public fun UriDsl.chatscript(i: ChatscriptUriDsl.() -> Unit) {
  ChatscriptUriDsl(this).apply(i)
}

@CamelDslMarker
public class ChatscriptUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("chatscript")
  }

  private var host: String = ""

  private var port: String = ""

  private var botName: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$botName")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$botName")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$botName")
  }

  public fun botName(botName: String) {
    this.botName = botName
    it.url("$host:$port/$botName")
  }

  public fun chatUserName(chatUserName: String) {
    it.property("chatUserName", chatUserName)
  }

  public fun resetChat(resetChat: String) {
    it.property("resetChat", resetChat)
  }

  public fun resetChat(resetChat: Boolean) {
    it.property("resetChat", resetChat.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
