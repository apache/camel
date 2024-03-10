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
 * Upload, download and manage files, folders, groups, collaborations, etc on Dropbox.
 */
public fun UriDsl.dropbox(i: DropboxUriDsl.() -> Unit) {
  DropboxUriDsl(this).apply(i)
}

@CamelDslMarker
public class DropboxUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dropbox")
  }

  private var operation: String = ""

  /**
   * The specific action (typically is a CRUD action) to perform on Dropbox remote folder.
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  /**
   * Name of the app registered to make API requests
   */
  public fun clientIdentifier(clientIdentifier: String) {
    it.property("clientIdentifier", clientIdentifier)
  }

  /**
   * A space-separated list of sub-strings to search for. A file matches only if it contains all the
   * sub-strings. If this option is not set, all files will be matched.
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * Original file or folder to move
   */
  public fun remotePath(remotePath: String) {
    it.property("remotePath", remotePath)
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
   * Optional folder or file to upload on Dropbox from the local filesystem. If this option has not
   * been configured then the message body is used as the content to upload.
   */
  public fun localPath(localPath: String) {
    it.property("localPath", localPath)
  }

  /**
   * Destination file or folder
   */
  public fun newRemotePath(newRemotePath: String) {
    it.property("newRemotePath", newRemotePath)
  }

  /**
   * Which mode to upload. in case of add the new file will be renamed if a file with the same name
   * already exists on dropbox. in case of force if a file with the same name already exists on
   * dropbox, this will be overwritten.
   */
  public fun uploadMode(uploadMode: String) {
    it.property("uploadMode", uploadMode)
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
   * To use an existing DbxClient instance as Dropbox client.
   */
  public fun client(client: String) {
    it.property("client", client)
  }

  /**
   * The access token to make API requests for a specific Dropbox user
   */
  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  /**
   * The apiKey to make API requests for a specific Dropbox user
   */
  public fun apiKey(apiKey: String) {
    it.property("apiKey", apiKey)
  }

  /**
   * The apiSecret to make API requests for a specific Dropbox user
   */
  public fun apiSecret(apiSecret: String) {
    it.property("apiSecret", apiSecret)
  }

  /**
   * The expire time to access token for a specific Dropbox user
   */
  public fun expireIn(expireIn: String) {
    it.property("expireIn", expireIn)
  }

  /**
   * The expire time to access token for a specific Dropbox user
   */
  public fun expireIn(expireIn: Int) {
    it.property("expireIn", expireIn.toString())
  }

  /**
   * The refresh token to refresh the access token for a specific Dropbox user
   */
  public fun refreshToken(refreshToken: String) {
    it.property("refreshToken", refreshToken)
  }
}
