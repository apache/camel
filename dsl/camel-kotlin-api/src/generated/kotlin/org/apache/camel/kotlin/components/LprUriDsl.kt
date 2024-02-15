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

public fun UriDsl.lpr(i: LprUriDsl.() -> Unit) {
  LprUriDsl(this).apply(i)
}

@CamelDslMarker
public class LprUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("lpr")
  }

  private var hostname: String = ""

  private var port: String = ""

  private var printername: String = ""

  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$hostname:$port/$printername")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$hostname:$port/$printername")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$hostname:$port/$printername")
  }

  public fun printername(printername: String) {
    this.printername = printername
    it.url("$hostname:$port/$printername")
  }

  public fun copies(copies: String) {
    it.property("copies", copies)
  }

  public fun copies(copies: Int) {
    it.property("copies", copies.toString())
  }

  public fun docFlavor(docFlavor: String) {
    it.property("docFlavor", docFlavor)
  }

  public fun flavor(flavor: String) {
    it.property("flavor", flavor)
  }

  public fun mediaSize(mediaSize: String) {
    it.property("mediaSize", mediaSize)
  }

  public fun mediaTray(mediaTray: String) {
    it.property("mediaTray", mediaTray)
  }

  public fun mimeType(mimeType: String) {
    it.property("mimeType", mimeType)
  }

  public fun orientation(orientation: String) {
    it.property("orientation", orientation)
  }

  public fun printerPrefix(printerPrefix: String) {
    it.property("printerPrefix", printerPrefix)
  }

  public fun sendToPrinter(sendToPrinter: String) {
    it.property("sendToPrinter", sendToPrinter)
  }

  public fun sendToPrinter(sendToPrinter: Boolean) {
    it.property("sendToPrinter", sendToPrinter.toString())
  }

  public fun sides(sides: String) {
    it.property("sides", sides)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
