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
 * Manage secrets using AWS Secrets Manager.
 */
public fun UriDsl.`aws-secrets-manager`(i: AwsSecretsManagerUriDsl.() -> Unit) {
  AwsSecretsManagerUriDsl(this).apply(i)
}

@CamelDslMarker
public class AwsSecretsManagerUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws-secrets-manager")
  }

  private var label: String = ""

  /**
   * Logical name
   */
  public fun label(label: String) {
    this.label = label
    it.url("$label")
  }

  /**
   * Set if the secret is binary or not
   */
  public fun binaryPayload(binaryPayload: String) {
    it.property("binaryPayload", binaryPayload)
  }

  /**
   * Set if the secret is binary or not
   */
  public fun binaryPayload(binaryPayload: Boolean) {
    it.property("binaryPayload", binaryPayload.toString())
  }

  /**
   * The operation to perform
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with the
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with the
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  /**
   * If we want to use a POJO request as body or not
   */
  public fun pojoRequest(pojoRequest: String) {
    it.property("pojoRequest", pojoRequest)
  }

  /**
   * If we want to use a POJO request as body or not
   */
  public fun pojoRequest(pojoRequest: Boolean) {
    it.property("pojoRequest", pojoRequest.toString())
  }

  /**
   * If using a profile credentials provider, this parameter will set the profile name
   */
  public fun profileCredentialsName(profileCredentialsName: String) {
    it.property("profileCredentialsName", profileCredentialsName)
  }

  /**
   * The region in which a Secrets Manager client needs to work. When using this parameter, the
   * configuration will expect the lowercase name of the region (for example, ap-east-1) You'll need to
   * use the name Region.EU_WEST_1.id()
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * Set the overriding uri endpoint. This option needs to be used in combination with
   * overrideEndpoint option
   */
  public fun uriEndpointOverride(uriEndpointOverride: String) {
    it.property("uriEndpointOverride", uriEndpointOverride)
  }

  /**
   * Set whether the Secrets Manager client should expect to load credentials through a profile
   * credentials provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the Secrets Manager client should expect to load credentials through a profile
   * credentials provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
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
   * To use an existing configured AWS Secrets Manager client
   */
  public fun secretsManagerClient(secretsManagerClient: String) {
    it.property("secretsManagerClient", secretsManagerClient)
  }

  /**
   * To define a proxy host when instantiating the Secrets Manager client
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * To define a proxy port when instantiating the Secrets Manager client
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * To define a proxy port when instantiating the Secrets Manager client
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the Secrets Manager client
   */
  public fun proxyProtocol(proxyProtocol: String) {
    it.property("proxyProtocol", proxyProtocol)
  }

  /**
   * Amazon AWS Access Key
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * Amazon AWS Secret Key
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  /**
   * Amazon AWS Session Token used when the user needs to assume an IAM role
   */
  public fun sessionToken(sessionToken: String) {
    it.property("sessionToken", sessionToken)
  }

  /**
   * If we want to trust all certificates in case of overriding the endpoint
   */
  public fun trustAllCertificates(trustAllCertificates: String) {
    it.property("trustAllCertificates", trustAllCertificates)
  }

  /**
   * If we want to trust all certificates in case of overriding the endpoint
   */
  public fun trustAllCertificates(trustAllCertificates: Boolean) {
    it.property("trustAllCertificates", trustAllCertificates.toString())
  }

  /**
   * Set whether the Translate client should expect to load credentials through a default
   * credentials provider or to expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the Translate client should expect to load credentials through a default
   * credentials provider or to expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the Secrets Manager client should expect to use Session Credentials. This is useful
   * in a situation in which the user needs to assume an IAM role for doing operations in Secrets
   * Manager.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the Secrets Manager client should expect to use Session Credentials. This is useful
   * in a situation in which the user needs to assume an IAM role for doing operations in Secrets
   * Manager.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
