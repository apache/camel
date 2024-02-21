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

  public fun secretsEngine(secretsEngine: String) {
    this.secretsEngine = secretsEngine
    it.url("$secretsEngine")
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun port(port: String) {
    it.property("port", port)
  }

  public fun scheme(scheme: String) {
    it.property("scheme", scheme)
  }

  public fun secretPath(secretPath: String) {
    it.property("secretPath", secretPath)
  }

  public fun vaultTemplate(vaultTemplate: String) {
    it.property("vaultTemplate", vaultTemplate)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun token(token: String) {
    it.property("token", token)
  }
}
