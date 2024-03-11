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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * To identify objects, scenes, and concepts in images on Huawei Cloud
 */
public fun UriDsl.`hwcloud-imagerecognition`(i: HwcloudImagerecognitionUriDsl.() -> Unit) {
  HwcloudImagerecognitionUriDsl(this).apply(i)
}

@CamelDslMarker
public class HwcloudImagerecognitionUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("hwcloud-imagerecognition")
  }

  private var operation: String = ""

  /**
   * Name of Image Recognition operation to perform, including celebrityRecognition and
   * tagRecognition
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  /**
   * Access key for the cloud user
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * Fully qualified Image Recognition service url. Carries higher precedence than region based
   * configuration.
   */
  public fun endpoint(endpoint: String) {
    it.property("endpoint", endpoint)
  }

  /**
   * Indicates the Base64 character string converted from the image. The size cannot exceed 10 MB.
   * The image resolution of the narrow sides must be greater than 15 pixels, and that of the wide
   * sides cannot exceed 4096 pixels.The supported image formats include JPG, PNG, and BMP. Configure
   * either this parameter or imageUrl, and this one carries higher precedence than imageUrl.
   */
  public fun imageContent(imageContent: String) {
    it.property("imageContent", imageContent)
  }

  /**
   * Indicates the URL of an image. The options are as follows: HTTP/HTTPS URLs on the public
   * network OBS URLs. To use OBS data, authorization is required, including service authorization,
   * temporary authorization, and anonymous public authorization. For details, see Configuring the
   * Access Permission of OBS. Configure either this parameter or imageContent, and this one carries
   * lower precedence than imageContent.
   */
  public fun imageUrl(imageUrl: String) {
    it.property("imageUrl", imageUrl)
  }

  /**
   * Cloud project ID
   */
  public fun projectId(projectId: String) {
    it.property("projectId", projectId)
  }

  /**
   * Proxy server ip/hostname
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * Proxy authentication password
   */
  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  /**
   * Proxy server port
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * Proxy server port
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * Proxy authentication user
   */
  public fun proxyUser(proxyUser: String) {
    it.property("proxyUser", proxyUser)
  }

  /**
   * Image Recognition service region. Currently only cn-north-1 and cn-north-4 are supported. This
   * is lower precedence than endpoint based configuration.
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * Secret key for the cloud user
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  /**
   * Configuration object for cloud service authentication
   */
  public fun serviceKeys(serviceKeys: String) {
    it.property("serviceKeys", serviceKeys)
  }

  /**
   * Indicates the language of the returned tags when the operation is tagRecognition, including zh
   * and en.
   */
  public fun tagLanguage(tagLanguage: String) {
    it.property("tagLanguage", tagLanguage)
  }

  /**
   * Indicates the maximum number of the returned tags when the operation is tagRecognition.
   */
  public fun tagLimit(tagLimit: String) {
    it.property("tagLimit", tagLimit)
  }

  /**
   * Indicates the maximum number of the returned tags when the operation is tagRecognition.
   */
  public fun tagLimit(tagLimit: Int) {
    it.property("tagLimit", tagLimit.toString())
  }

  /**
   * Indicates the threshold of confidence. When the operation is tagRecognition, this parameter
   * ranges from 0 to 100. Tags whose confidence score is lower than the threshold will not be
   * returned. The default value is 60. When the operation is celebrityRecognition, this parameter
   * ranges from 0 to 1. Labels whose confidence score is lower than the threshold will not be
   * returned. The default value is 0.48.
   */
  public fun threshold(threshold: String) {
    it.property("threshold", threshold)
  }

  /**
   * Indicates the threshold of confidence. When the operation is tagRecognition, this parameter
   * ranges from 0 to 100. Tags whose confidence score is lower than the threshold will not be
   * returned. The default value is 60. When the operation is celebrityRecognition, this parameter
   * ranges from 0 to 1. Labels whose confidence score is lower than the threshold will not be
   * returned. The default value is 0.48.
   */
  public fun threshold(threshold: Double) {
    it.property("threshold", threshold.toString())
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
   * Ignore SSL verification
   */
  public fun ignoreSslVerification(ignoreSslVerification: String) {
    it.property("ignoreSslVerification", ignoreSslVerification)
  }

  /**
   * Ignore SSL verification
   */
  public fun ignoreSslVerification(ignoreSslVerification: Boolean) {
    it.property("ignoreSslVerification", ignoreSslVerification.toString())
  }
}
