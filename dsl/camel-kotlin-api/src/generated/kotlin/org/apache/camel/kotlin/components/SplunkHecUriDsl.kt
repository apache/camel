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
 * The splunk component allows publishing events in Splunk using the HTTP Event Collector.
 */
public fun UriDsl.`splunk-hec`(i: SplunkHecUriDsl.() -> Unit) {
  SplunkHecUriDsl(this).apply(i)
}

@CamelDslMarker
public class SplunkHecUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("splunk-hec")
  }

  private var splunkURL: String = ""

  /**
   * Splunk Host and Port (example: my_splunk_server:8089)
   */
  public fun splunkURL(splunkURL: String) {
    this.splunkURL = splunkURL
    it.url("$splunkURL")
  }

  /**
   * Send only the message body
   */
  public fun bodyOnly(bodyOnly: String) {
    it.property("bodyOnly", bodyOnly)
  }

  /**
   * Send only the message body
   */
  public fun bodyOnly(bodyOnly: Boolean) {
    it.property("bodyOnly", bodyOnly.toString())
  }

  /**
   * Send only message headers
   */
  public fun headersOnly(headersOnly: String) {
    it.property("headersOnly", headersOnly)
  }

  /**
   * Send only message headers
   */
  public fun headersOnly(headersOnly: Boolean) {
    it.property("headersOnly", headersOnly.toString())
  }

  /**
   * Splunk host field of the event message. This is not the Splunk host to connect to.
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * Splunk index to write to
   */
  public fun index(index: String) {
    it.property("index", index)
  }

  /**
   * Splunk source argument
   */
  public fun source(source: String) {
    it.property("source", source)
  }

  /**
   * Splunk sourcetype argument
   */
  public fun sourceType(sourceType: String) {
    it.property("sourceType", sourceType)
  }

  /**
   * Splunk endpoint Defaults to /services/collector/event To write RAW data like JSON use
   * /services/collector/raw For a list of all endpoints refer to splunk documentation (HTTP Event
   * Collector REST API endpoints) Example for Spunk 8.2.x:
   * https://docs.splunk.com/Documentation/SplunkCloud/8.2.2203/Data/HECRESTendpoints To extract
   * timestamps in Splunk8.0 /services/collector/eventauto_extract_timestamp=true Remember to utilize
   * RAW{} for questionmarks or slashes in parameters.
   */
  public fun splunkEndpoint(splunkEndpoint: String) {
    it.property("splunkEndpoint", splunkEndpoint)
  }

  /**
   * SSL configuration
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * Time this even occurred. By default, the time will be when this event hits the splunk server.
   */
  public fun time(time: String) {
    it.property("time", time)
  }

  /**
   * Time this even occurred. By default, the time will be when this event hits the splunk server.
   */
  public fun time(time: Int) {
    it.property("time", time.toString())
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
   * Contact HEC over https.
   */
  public fun https(https: String) {
    it.property("https", https)
  }

  /**
   * Contact HEC over https.
   */
  public fun https(https: Boolean) {
    it.property("https", https.toString())
  }

  /**
   * Splunk HEC TLS verification.
   */
  public fun skipTlsVerify(skipTlsVerify: String) {
    it.property("skipTlsVerify", skipTlsVerify)
  }

  /**
   * Splunk HEC TLS verification.
   */
  public fun skipTlsVerify(skipTlsVerify: Boolean) {
    it.property("skipTlsVerify", skipTlsVerify.toString())
  }

  /**
   * Splunk HEC token (this is the token created for HEC and not the user's token)
   */
  public fun token(token: String) {
    it.property("token", token)
  }
}
