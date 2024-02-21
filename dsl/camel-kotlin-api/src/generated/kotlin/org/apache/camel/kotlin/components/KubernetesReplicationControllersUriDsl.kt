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

public
    fun UriDsl.`kubernetes-replication-controllers`(i: KubernetesReplicationControllersUriDsl.() -> Unit) {
  KubernetesReplicationControllersUriDsl(this).apply(i)
}

@CamelDslMarker
public class KubernetesReplicationControllersUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("kubernetes-replication-controllers")
  }

  private var masterUrl: String = ""

  public fun masterUrl(masterUrl: String) {
    this.masterUrl = masterUrl
    it.url("$masterUrl")
  }

  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  public fun dnsDomain(dnsDomain: String) {
    it.property("dnsDomain", dnsDomain)
  }

  public fun kubernetesClient(kubernetesClient: String) {
    it.property("kubernetesClient", kubernetesClient)
  }

  public fun namespace(namespace: String) {
    it.property("namespace", namespace)
  }

  public fun portName(portName: String) {
    it.property("portName", portName)
  }

  public fun portProtocol(portProtocol: String) {
    it.property("portProtocol", portProtocol)
  }

  public fun crdGroup(crdGroup: String) {
    it.property("crdGroup", crdGroup)
  }

  public fun crdName(crdName: String) {
    it.property("crdName", crdName)
  }

  public fun crdPlural(crdPlural: String) {
    it.property("crdPlural", crdPlural)
  }

  public fun crdScope(crdScope: String) {
    it.property("crdScope", crdScope)
  }

  public fun crdVersion(crdVersion: String) {
    it.property("crdVersion", crdVersion)
  }

  public fun labelKey(labelKey: String) {
    it.property("labelKey", labelKey)
  }

  public fun labelValue(labelValue: String) {
    it.property("labelValue", labelValue)
  }

  public fun poolSize(poolSize: String) {
    it.property("poolSize", poolSize)
  }

  public fun poolSize(poolSize: Int) {
    it.property("poolSize", poolSize.toString())
  }

  public fun resourceName(resourceName: String) {
    it.property("resourceName", resourceName)
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

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun caCertData(caCertData: String) {
    it.property("caCertData", caCertData)
  }

  public fun caCertFile(caCertFile: String) {
    it.property("caCertFile", caCertFile)
  }

  public fun clientCertData(clientCertData: String) {
    it.property("clientCertData", clientCertData)
  }

  public fun clientCertFile(clientCertFile: String) {
    it.property("clientCertFile", clientCertFile)
  }

  public fun clientKeyAlgo(clientKeyAlgo: String) {
    it.property("clientKeyAlgo", clientKeyAlgo)
  }

  public fun clientKeyData(clientKeyData: String) {
    it.property("clientKeyData", clientKeyData)
  }

  public fun clientKeyFile(clientKeyFile: String) {
    it.property("clientKeyFile", clientKeyFile)
  }

  public fun clientKeyPassphrase(clientKeyPassphrase: String) {
    it.property("clientKeyPassphrase", clientKeyPassphrase)
  }

  public fun oauthToken(oauthToken: String) {
    it.property("oauthToken", oauthToken)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun trustCerts(trustCerts: String) {
    it.property("trustCerts", trustCerts)
  }

  public fun trustCerts(trustCerts: Boolean) {
    it.property("trustCerts", trustCerts.toString())
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
