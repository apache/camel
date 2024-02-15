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

public fun UriDsl.`azure-key-vault`(i: AzureKeyVaultUriDsl.() -> Unit) {
  AzureKeyVaultUriDsl(this).apply(i)
}

@CamelDslMarker
public class AzureKeyVaultUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("azure-key-vault")
  }

  private var vaultName: String = ""

  public fun vaultName(vaultName: String) {
    this.vaultName = vaultName
    it.url("$vaultName")
  }

  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun secretClient(secretClient: String) {
    it.property("secretClient", secretClient)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun clientSecret(clientSecret: String) {
    it.property("clientSecret", clientSecret)
  }

  public fun tenantId(tenantId: String) {
    it.property("tenantId", tenantId)
  }
}
