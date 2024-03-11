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
 * Face Recognition Service (FRS) is an intelligent service that uses computers to process, analyze,
 * and understand facial images based on human facial features.
 */
public fun UriDsl.`hwcloud-frs`(i: HwcloudFrsUriDsl.() -> Unit) {
  HwcloudFrsUriDsl(this).apply(i)
}

@CamelDslMarker
public class HwcloudFrsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("hwcloud-frs")
  }

  private var operation: String = ""

  /**
   * Name of Face Recognition operation to perform, including faceDetection, faceVerification and
   * faceLiveDetection
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
   * This param is mandatory when the operation is faceLiveDetection, indicating the action code
   * sequence list. Actions are separated by commas (,). Currently, the following actions are
   * supported: 1: Shake the head to the left. 2: Shake the head to the right. 3: Nod the head. 4:
   * Mouth movement.
   */
  public fun actions(actions: String) {
    it.property("actions", actions)
  }

  /**
   * This param can be used when the operation is faceLiveDetection, indicating the action time
   * array. The length of the array is the same as the number of actions. Each item contains the start
   * time and end time of the action in the corresponding sequence. The unit is the milliseconds from
   * the video start time.
   */
  public fun actionTimes(actionTimes: String) {
    it.property("actionTimes", actionTimes)
  }

  /**
   * This param can be used when operation is faceVerification, indicating the Base64 character
   * string converted from the other image. It needs to be configured if imageBase64 is set. The image
   * size cannot exceed 10 MB. The image resolution of the narrow sides must be greater than 15 pixels,
   * and that of the wide sides cannot exceed 4096 pixels. The supported image formats include JPG,
   * PNG, and BMP.
   */
  public fun anotherImageBase64(anotherImageBase64: String) {
    it.property("anotherImageBase64", anotherImageBase64)
  }

  /**
   * This param can be used when operation is faceVerification, indicating the local file path of
   * the other image. It needs to be configured if imageFilePath is set. Image size cannot exceed 8 MB,
   * and it is recommended that the image size be less than 1 MB.
   */
  public fun anotherImageFilePath(anotherImageFilePath: String) {
    it.property("anotherImageFilePath", anotherImageFilePath)
  }

  /**
   * This param can be used when operation is faceVerification, indicating the URL of the other
   * image. It needs to be configured if imageUrl is set. The options are as follows: 1.HTTP/HTTPS URLs
   * on the public network 2.OBS URLs. To use OBS data, authorization is required, including service
   * authorization, temporary authorization, and anonymous public authorization. For details, see
   * Configuring the Access Permission of OBS.
   */
  public fun anotherImageUrl(anotherImageUrl: String) {
    it.property("anotherImageUrl", anotherImageUrl)
  }

  /**
   * Fully qualified Face Recognition service url. Carries higher precedence than region based
   * configuration.
   */
  public fun endpoint(endpoint: String) {
    it.property("endpoint", endpoint)
  }

  /**
   * This param can be used when operation is faceDetection or faceVerification, indicating the
   * Base64 character string converted from an image. Any one of imageBase64, imageUrl and
   * imageFilePath needs to be set, and the priority is imageBase64 imageUrl imageFilePath. The Image
   * size cannot exceed 10 MB. The image resolution of the narrow sides must be greater than 15 pixels,
   * and that of the wide sides cannot exceed 4096 pixels. The supported image formats include JPG,
   * PNG, and BMP.
   */
  public fun imageBase64(imageBase64: String) {
    it.property("imageBase64", imageBase64)
  }

  /**
   * This param can be used when operation is faceDetection or faceVerification, indicating the
   * local image file path. Any one of imageBase64, imageUrl and imageFilePath needs to be set, and the
   * priority is imageBase64 imageUrl imageFilePath. Image size cannot exceed 8 MB, and it is
   * recommended that the image size be less than 1 MB.
   */
  public fun imageFilePath(imageFilePath: String) {
    it.property("imageFilePath", imageFilePath)
  }

  /**
   * This param can be used when operation is faceDetection or faceVerification, indicating the URL
   * of an image. Any one of imageBase64, imageUrl and imageFilePath needs to be set, and the priority
   * is imageBase64 imageUrl imageFilePath. The options are as follows: 1.HTTP/HTTPS URLs on the public
   * network 2.OBS URLs. To use OBS data, authorization is required, including service authorization,
   * temporary authorization, and anonymous public authorization. For details, see Configuring the
   * Access Permission of OBS.
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
   * Face Recognition service region. Currently only cn-north-1 and cn-north-4 are supported. This
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
   * This param can be used when operation is faceLiveDetection, indicating the Base64 character
   * string converted from a video. Any one of videoBase64, videoUrl and videoFilePath needs to be set,
   * and the priority is videoBase64 videoUrl videoFilePath. Requirements are as follows: 1.The video
   * size after Base64 encoding cannot exceed 8 MB. It is recommended that the video file be compressed
   * to 200 KB to 2 MB on the client. 2.The video duration must be 1 to 15 seconds. 3.The recommended
   * frame rate is 10 fps to 30 fps. 4.The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or
   * MOV. 5.The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.
   */
  public fun videoBase64(videoBase64: String) {
    it.property("videoBase64", videoBase64)
  }

  /**
   * This param can be used when operation is faceLiveDetection, indicating the local video file
   * path. Any one of videoBase64, videoUrl and videoFilePath needs to be set, and the priority is
   * videoBase64 videoUrl videoFilePath. The video requirements are as follows: 1.The size of a video
   * file cannot exceed 8 MB. It is recommended that the video file be compressed to 200 KB to 2 MB on
   * the client. 2.The video duration must be 1 to 15 seconds. 3.The recommended frame rate is 10 fps
   * to 30 fps. 4.The encapsulation format can be MP4, AVI, FLV, WEBM, ASF, or MOV. 5.The video
   * encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or WMV3.
   */
  public fun videoFilePath(videoFilePath: String) {
    it.property("videoFilePath", videoFilePath)
  }

  /**
   * This param can be used when operation is faceLiveDetection, indicating the URL of a video. Any
   * one of videoBase64, videoUrl and videoFilePath needs to be set, and the priority is videoBase64
   * videoUrl videoFilePath. Currently, only the URL of an OBS bucket on HUAWEI CLOUD is supported and
   * FRS must have the permission to read data in the OBS bucket. For details about how to enable the
   * read permission, see Service Authorization. The video requirements are as follows: 1.The video
   * size after Base64 encoding cannot exceed 8 MB. 2.The video duration must be 1 to 15 seconds. 3.The
   * recommended frame rate is 10 fps to 30 fps. 4.The encapsulation format can be MP4, AVI, FLV, WEBM,
   * ASF, or MOV. 5.The video encoding format can be H.261, H.263, H.264, HEVC, VC-1, VP8, VP9, or
   * WMV3.
   */
  public fun videoUrl(videoUrl: String) {
    it.property("videoUrl", videoUrl)
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
