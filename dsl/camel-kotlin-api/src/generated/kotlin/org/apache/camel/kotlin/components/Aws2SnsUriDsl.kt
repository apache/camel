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

  public fun topicNameOrArn(topicNameOrArn: String) {
    this.topicNameOrArn = topicNameOrArn
    it.url("$topicNameOrArn")
  }

  public fun autoCreateTopic(autoCreateTopic: String) {
    it.property("autoCreateTopic", autoCreateTopic)
  }

  public fun autoCreateTopic(autoCreateTopic: Boolean) {
    it.property("autoCreateTopic", autoCreateTopic.toString())
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun kmsMasterKeyId(kmsMasterKeyId: String) {
    it.property("kmsMasterKeyId", kmsMasterKeyId)
  }

  public fun messageDeduplicationIdStrategy(messageDeduplicationIdStrategy: String) {
    it.property("messageDeduplicationIdStrategy", messageDeduplicationIdStrategy)
  }

  public fun messageGroupIdStrategy(messageGroupIdStrategy: String) {
    it.property("messageGroupIdStrategy", messageGroupIdStrategy)
  }

  public fun messageStructure(messageStructure: String) {
    it.property("messageStructure", messageStructure)
  }

  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  public fun queueArn(queueArn: String) {
    it.property("queueArn", queueArn)
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: String) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled)
  }

  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: Boolean) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled.toString())
  }

  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  public fun subscribeSNStoSQS(subscribeSNStoSQS: String) {
    it.property("subscribeSNStoSQS", subscribeSNStoSQS)
  }

  public fun subscribeSNStoSQS(subscribeSNStoSQS: Boolean) {
    it.property("subscribeSNStoSQS", subscribeSNStoSQS.toString())
  }

  public fun uriEndpointOverride(uriEndpointOverride: String) {
    it.property("uriEndpointOverride", uriEndpointOverride)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun amazonSNSClient(amazonSNSClient: String) {
    it.property("amazonSNSClient", amazonSNSClient)
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun proxyProtocol(proxyProtocol: String) {
    it.property("proxyProtocol", proxyProtocol)
  }

  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  public fun profileCredentialsName(profileCredentialsName: String) {
    it.property("profileCredentialsName", profileCredentialsName)
  }

  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  public fun sessionToken(sessionToken: String) {
    it.property("sessionToken", sessionToken)
  }

  public fun trustAllCertificates(trustAllCertificates: String) {
    it.property("trustAllCertificates", trustAllCertificates)
  }

  public fun trustAllCertificates(trustAllCertificates: Boolean) {
    it.property("trustAllCertificates", trustAllCertificates.toString())
  }

  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
