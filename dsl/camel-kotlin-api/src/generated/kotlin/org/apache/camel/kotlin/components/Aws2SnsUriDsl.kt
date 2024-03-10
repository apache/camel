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
 * Send messages to AWS Simple Notification Topic.
 */
public fun UriDsl.`aws2-sns`(i: Aws2SnsUriDsl.() -> Unit) {
  Aws2SnsUriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2SnsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-sns")
  }

  private var topicNameOrArn: String = ""

  /**
   * Topic name or ARN
   */
  public fun topicNameOrArn(topicNameOrArn: String) {
    this.topicNameOrArn = topicNameOrArn
    it.url("$topicNameOrArn")
  }

  /**
   * Setting the auto-creation of the topic
   */
  public fun autoCreateTopic(autoCreateTopic: String) {
    it.property("autoCreateTopic", autoCreateTopic)
  }

  /**
   * Setting the auto-creation of the topic
   */
  public fun autoCreateTopic(autoCreateTopic: Boolean) {
    it.property("autoCreateTopic", autoCreateTopic.toString())
  }

  /**
   * To use a custom HeaderFilterStrategy to map headers to/from Camel.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * The ID of an AWS-managed customer master key (CMK) for Amazon SNS or a custom CMK.
   */
  public fun kmsMasterKeyId(kmsMasterKeyId: String) {
    it.property("kmsMasterKeyId", kmsMasterKeyId)
  }

  /**
   * Only for FIFO Topic. Strategy for setting the messageDeduplicationId on the message. It can be
   * one of the following options: useExchangeId, useContentBasedDeduplication. For the
   * useContentBasedDeduplication option, no messageDeduplicationId will be set on the message.
   */
  public fun messageDeduplicationIdStrategy(messageDeduplicationIdStrategy: String) {
    it.property("messageDeduplicationIdStrategy", messageDeduplicationIdStrategy)
  }

  /**
   * Only for FIFO Topic. Strategy for setting the messageGroupId on the message. It can be one of
   * the following options: useConstant, useExchangeId, usePropertyValue. For the usePropertyValue
   * option, the value of property CamelAwsMessageGroupId will be used.
   */
  public fun messageGroupIdStrategy(messageGroupIdStrategy: String) {
    it.property("messageGroupIdStrategy", messageGroupIdStrategy)
  }

  /**
   * The message structure to use such as json
   */
  public fun messageStructure(messageStructure: String) {
    it.property("messageStructure", messageStructure)
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
   * The policy for this topic. Is loaded by default from classpath, but you can prefix with
   * classpath:, file:, or http: to load the resource from different systems.
   */
  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  /**
   * The ARN endpoint to subscribe to
   */
  public fun queueArn(queueArn: String) {
    it.property("queueArn", queueArn)
  }

  /**
   * The region in which the SNS client needs to work. When using this parameter, the configuration
   * will expect the lowercase name of the region (for example, ap-east-1) You'll need to use the name
   * Region.EU_WEST_1.id()
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * Define if Server Side Encryption is enabled or not on the topic
   */
  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: String) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled)
  }

  /**
   * Define if Server Side Encryption is enabled or not on the topic
   */
  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: Boolean) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled.toString())
  }

  /**
   * The subject which is used if the message header 'CamelAwsSnsSubject' is not present.
   */
  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  /**
   * Define if the subscription between SNS Topic and SQS must be done or not
   */
  public fun subscribeSNStoSQS(subscribeSNStoSQS: String) {
    it.property("subscribeSNStoSQS", subscribeSNStoSQS)
  }

  /**
   * Define if the subscription between SNS Topic and SQS must be done or not
   */
  public fun subscribeSNStoSQS(subscribeSNStoSQS: Boolean) {
    it.property("subscribeSNStoSQS", subscribeSNStoSQS.toString())
  }

  /**
   * Set the overriding uri endpoint. This option needs to be used in combination with
   * overrideEndpoint option
   */
  public fun uriEndpointOverride(uriEndpointOverride: String) {
    it.property("uriEndpointOverride", uriEndpointOverride)
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
   * To use the AmazonSNS as the client
   */
  public fun amazonSNSClient(amazonSNSClient: String) {
    it.property("amazonSNSClient", amazonSNSClient)
  }

  /**
   * To define a proxy host when instantiating the SNS client
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * To define a proxy port when instantiating the SNS client
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * To define a proxy port when instantiating the SNS client
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the SNS client
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
   * If using a profile credentials provider, this parameter will set the profile name
   */
  public fun profileCredentialsName(profileCredentialsName: String) {
    it.property("profileCredentialsName", profileCredentialsName)
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
   * Set whether the SNS client should expect to load credentials on an AWS infra instance or to
   * expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the SNS client should expect to load credentials on an AWS infra instance or to
   * expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the SNS client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the SNS client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  /**
   * Set whether the SNS client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in SNS.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the SNS client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in SNS.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
