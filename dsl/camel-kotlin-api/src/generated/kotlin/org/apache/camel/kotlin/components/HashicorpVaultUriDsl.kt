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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Manage secrets in Hashicorp Vault Service
 */
public fun UriDsl.`hashicorp-vault`(i: HashicorpVaultUriDsl.() -> Unit) {
  HashicorpVaultUriDsl(this).apply(i)
}

@CamelDslMarker
public class HashicorpVaultUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("hashicorp-vault")
  }

  private var secretsEngine: String = ""

  /**
   * Vault Name to be used
   */
  public fun secretsEngine(secretsEngine: String) {
    this.secretsEngine = secretsEngine
    it.url("$secretsEngine")
  }

  /**
   * Hashicorp Vault instance host to be used
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * Operation to be performed
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Hashicorp Vault instance port to be used
   */
  public fun port(port: String) {
    it.property("port", port)
  }

  /**
   * Hashicorp Vault instance scheme to be used
   */
  public fun scheme(scheme: String) {
    it.property("scheme", scheme)
  }

  /**
   * Hashicorp Vault instance secret Path to be used
   */
  public fun secretPath(secretPath: String) {
    it.property("secretPath", secretPath)
  }

  /**
   * Instance of Vault template
   */
  public fun vaultTemplate(vaultTemplate: String) {
    it.property("vaultTemplate", vaultTemplate)
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
   * Token to be used
   */
  public fun token(token: String) {
    it.property("token", token)
  }
}
