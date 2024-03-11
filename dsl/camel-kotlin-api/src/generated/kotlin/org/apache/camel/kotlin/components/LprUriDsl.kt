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
 * Send print jobs to printers.
 */
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

  /**
   * Hostname of the printer
   */
  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$hostname:$port/$printername")
  }

  /**
   * Port number of the printer
   */
  public fun port(port: String) {
    this.port = port
    it.url("$hostname:$port/$printername")
  }

  /**
   * Port number of the printer
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$hostname:$port/$printername")
  }

  /**
   * Name of the printer
   */
  public fun printername(printername: String) {
    this.printername = printername
    it.url("$hostname:$port/$printername")
  }

  /**
   * Number of copies to print
   */
  public fun copies(copies: String) {
    it.property("copies", copies)
  }

  /**
   * Number of copies to print
   */
  public fun copies(copies: Int) {
    it.property("copies", copies.toString())
  }

  /**
   * Sets DocFlavor to use.
   */
  public fun docFlavor(docFlavor: String) {
    it.property("docFlavor", docFlavor)
  }

  /**
   * Sets DocFlavor to use.
   */
  public fun flavor(flavor: String) {
    it.property("flavor", flavor)
  }

  /**
   * Sets the stationary as defined by enumeration names in the
   * javax.print.attribute.standard.MediaSizeName API. The default setting is to use North American
   * Letter sized stationary. The value's case is ignored, e.g. values of iso_a4 and ISO_A4 may be
   * used.
   */
  public fun mediaSize(mediaSize: String) {
    it.property("mediaSize", mediaSize)
  }

  /**
   * Sets MediaTray supported by the javax.print.DocFlavor API, for example upper,middle etc.
   */
  public fun mediaTray(mediaTray: String) {
    it.property("mediaTray", mediaTray)
  }

  /**
   * Sets mimeTypes supported by the javax.print.DocFlavor API
   */
  public fun mimeType(mimeType: String) {
    it.property("mimeType", mimeType)
  }

  /**
   * Sets the page orientation.
   */
  public fun orientation(orientation: String) {
    it.property("orientation", orientation)
  }

  /**
   * Sets the prefix name of the printer, it is useful when the printer name does not start with
   * //hostname/printer
   */
  public fun printerPrefix(printerPrefix: String) {
    it.property("printerPrefix", printerPrefix)
  }

  /**
   * etting this option to false prevents sending of the print data to the printer
   */
  public fun sendToPrinter(sendToPrinter: String) {
    it.property("sendToPrinter", sendToPrinter)
  }

  /**
   * etting this option to false prevents sending of the print data to the printer
   */
  public fun sendToPrinter(sendToPrinter: Boolean) {
    it.property("sendToPrinter", sendToPrinter.toString())
  }

  /**
   * Sets one sided or two sided printing based on the javax.print.attribute.standard.Sides API
   */
  public fun sides(sides: String) {
    it.property("sides", sides)
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
