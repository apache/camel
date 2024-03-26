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
 * Perform operations on Openshift Deployment Configs and get notified on Deployment Config changes.
 */
public fun UriDsl.`openshift-deploymentconfigs`(i: OpenshiftDeploymentconfigsUriDsl.() -> Unit) {
  OpenshiftDeploymentconfigsUriDsl(this).apply(i)
}

@CamelDslMarker
public class OpenshiftDeploymentconfigsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("openshift-deploymentconfigs")
  }

  private var masterUrl: String = ""

  /**
   * URL to a remote Kubernetes API server. This should only be used when your Camel application is
   * connecting from outside Kubernetes. If you run your Camel application inside Kubernetes, then you
   * can use local or client as the URL to tell Camel to run in local mode. If you connect remotely to
   * Kubernetes, then you may also need some of the many other configuration options for secured
   * connection with certificates, etc.
   */
  public fun masterUrl(masterUrl: String) {
    this.masterUrl = masterUrl
    it.url("$masterUrl")
  }

  /**
   * The Kubernetes API Version to use
   */
  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  /**
   * The dns domain, used for ServiceCall EIP
   */
  public fun dnsDomain(dnsDomain: String) {
    it.property("dnsDomain", dnsDomain)
  }

  /**
   * Default KubernetesClient to use if provided
   */
  public fun kubernetesClient(kubernetesClient: String) {
    it.property("kubernetesClient", kubernetesClient)
  }

  /**
   * The namespace
   */
  public fun namespace(namespace: String) {
    it.property("namespace", namespace)
  }

  /**
   * The port name, used for ServiceCall EIP
   */
  public fun portName(portName: String) {
    it.property("portName", portName)
  }

  /**
   * The port protocol, used for ServiceCall EIP
   */
  public fun portProtocol(portProtocol: String) {
    it.property("portProtocol", portProtocol)
  }

  /**
   * The Consumer CRD Resource Group we would like to watch
   */
  public fun crdGroup(crdGroup: String) {
    it.property("crdGroup", crdGroup)
  }

  /**
   * The Consumer CRD Resource name we would like to watch
   */
  public fun crdName(crdName: String) {
    it.property("crdName", crdName)
  }

  /**
   * The Consumer CRD Resource Plural we would like to watch
   */
  public fun crdPlural(crdPlural: String) {
    it.property("crdPlural", crdPlural)
  }

  /**
   * The Consumer CRD Resource Scope we would like to watch
   */
  public fun crdScope(crdScope: String) {
    it.property("crdScope", crdScope)
  }

  /**
   * The Consumer CRD Resource Version we would like to watch
   */
  public fun crdVersion(crdVersion: String) {
    it.property("crdVersion", crdVersion)
  }

  /**
   * The Consumer Label key when watching at some resources
   */
  public fun labelKey(labelKey: String) {
    it.property("labelKey", labelKey)
  }

  /**
   * The Consumer Label value when watching at some resources
   */
  public fun labelValue(labelValue: String) {
    it.property("labelValue", labelValue)
  }

  /**
   * The Consumer pool size
   */
  public fun poolSize(poolSize: String) {
    it.property("poolSize", poolSize)
  }

  /**
   * The Consumer pool size
   */
  public fun poolSize(poolSize: Int) {
    it.property("poolSize", poolSize.toString())
  }

  /**
   * The Consumer Resource Name we would like to watch
   */
  public fun resourceName(resourceName: String) {
    it.property("resourceName", resourceName)
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
   * Producer operation to do on Kubernetes
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
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
   * Connection timeout in milliseconds to use when making requests to the Kubernetes API server.
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Connection timeout in milliseconds to use when making requests to the Kubernetes API server.
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * The CA Cert Data
   */
  public fun caCertData(caCertData: String) {
    it.property("caCertData", caCertData)
  }

  /**
   * The CA Cert File
   */
  public fun caCertFile(caCertFile: String) {
    it.property("caCertFile", caCertFile)
  }

  /**
   * The Client Cert Data
   */
  public fun clientCertData(clientCertData: String) {
    it.property("clientCertData", clientCertData)
  }

  /**
   * The Client Cert File
   */
  public fun clientCertFile(clientCertFile: String) {
    it.property("clientCertFile", clientCertFile)
  }

  /**
   * The Key Algorithm used by the client
   */
  public fun clientKeyAlgo(clientKeyAlgo: String) {
    it.property("clientKeyAlgo", clientKeyAlgo)
  }

  /**
   * The Client Key data
   */
  public fun clientKeyData(clientKeyData: String) {
    it.property("clientKeyData", clientKeyData)
  }

  /**
   * The Client Key file
   */
  public fun clientKeyFile(clientKeyFile: String) {
    it.property("clientKeyFile", clientKeyFile)
  }

  /**
   * The Client Key Passphrase
   */
  public fun clientKeyPassphrase(clientKeyPassphrase: String) {
    it.property("clientKeyPassphrase", clientKeyPassphrase)
  }

  /**
   * The Auth Token
   */
  public fun oauthToken(oauthToken: String) {
    it.property("oauthToken", oauthToken)
  }

  /**
   * Password to connect to Kubernetes
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Define if the certs we used are trusted anyway or not
   */
  public fun trustCerts(trustCerts: String) {
    it.property("trustCerts", trustCerts)
  }

  /**
   * Define if the certs we used are trusted anyway or not
   */
  public fun trustCerts(trustCerts: Boolean) {
    it.property("trustCerts", trustCerts.toString())
  }

  /**
   * Username to connect to Kubernetes
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
