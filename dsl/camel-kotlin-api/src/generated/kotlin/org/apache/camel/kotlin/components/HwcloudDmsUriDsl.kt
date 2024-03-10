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
 * To integrate with a fully managed, high-performance message queuing service on Huawei Cloud
 */
public fun UriDsl.`hwcloud-dms`(i: HwcloudDmsUriDsl.() -> Unit) {
  HwcloudDmsUriDsl(this).apply(i)
}

@CamelDslMarker
public class HwcloudDmsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("hwcloud-dms")
  }

  private var operation: String = ""

  /**
   * Operation to be performed
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
   * The username of a RabbitMQ instance. This option is mandatory when creating a RabbitMQ
   * instance.
   */
  public fun accessUser(accessUser: String) {
    it.property("accessUser", accessUser)
  }

  /**
   * The ID of an available zone. This option is mandatory when creating an instance and it cannot
   * be an empty array.
   */
  public fun availableZones(availableZones: String) {
    it.property("availableZones", availableZones)
  }

  /**
   * DMS url. Carries higher precedence than region parameter based client initialization
   */
  public fun endpoint(endpoint: String) {
    it.property("endpoint", endpoint)
  }

  /**
   * The message engine. Either kafka or rabbitmq. If the parameter is not specified, all instances
   * will be queried
   */
  public fun engine(engine: String) {
    it.property("engine", engine)
  }

  /**
   * The version of the message engine. This option is mandatory when creating an instance.
   */
  public fun engineVersion(engineVersion: String) {
    it.property("engineVersion", engineVersion)
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

  /**
   * The id of the instance. This option is mandatory when deleting or querying an instance
   */
  public fun instanceId(instanceId: String) {
    it.property("instanceId", instanceId)
  }

  /**
   * The password for logging in to the Kafka Manager. This option is mandatory when creating a
   * Kafka instance.
   */
  public fun kafkaManagerPassword(kafkaManagerPassword: String) {
    it.property("kafkaManagerPassword", kafkaManagerPassword)
  }

  /**
   * The username for logging in to the Kafka Manager. This option is mandatory when creating a
   * Kafka instance.
   */
  public fun kafkaManagerUser(kafkaManagerUser: String) {
    it.property("kafkaManagerUser", kafkaManagerUser)
  }

  /**
   * The name of the instance for creating and updating an instance. This option is mandatory when
   * creating an instance
   */
  public fun name(name: String) {
    it.property("name", name)
  }

  /**
   * The maximum number of partitions in a Kafka instance. This option is mandatory when creating a
   * Kafka instance.
   */
  public fun partitionNum(partitionNum: String) {
    it.property("partitionNum", partitionNum)
  }

  /**
   * The maximum number of partitions in a Kafka instance. This option is mandatory when creating a
   * Kafka instance.
   */
  public fun partitionNum(partitionNum: Int) {
    it.property("partitionNum", partitionNum.toString())
  }

  /**
   * The password of a RabbitMQ instance. This option is mandatory when creating a RabbitMQ
   * instance.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * The product ID. This option is mandatory when creating an instance.
   */
  public fun productId(productId: String) {
    it.property("productId", productId)
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
   * DMS service region
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
   * The security group which the instance belongs to. This option is mandatory when creating an
   * instance.
   */
  public fun securityGroupId(securityGroupId: String) {
    it.property("securityGroupId", securityGroupId)
  }

  /**
   * Configuration object for cloud service authentication
   */
  public fun serviceKeys(serviceKeys: String) {
    it.property("serviceKeys", serviceKeys)
  }

  /**
   * The baseline bandwidth of a Kafka instance. This option is mandatory when creating a Kafka
   * instance.
   */
  public fun specification(specification: String) {
    it.property("specification", specification)
  }

  /**
   * The message storage space. This option is mandatory when creating an instance.
   */
  public fun storageSpace(storageSpace: String) {
    it.property("storageSpace", storageSpace)
  }

  /**
   * The message storage space. This option is mandatory when creating an instance.
   */
  public fun storageSpace(storageSpace: Int) {
    it.property("storageSpace", storageSpace.toString())
  }

  /**
   * The storage I/O specification. This option is mandatory when creating an instance.
   */
  public fun storageSpecCode(storageSpecCode: String) {
    it.property("storageSpecCode", storageSpecCode)
  }

  /**
   * The subnet ID. This option is mandatory when creating an instance.
   */
  public fun subnetId(subnetId: String) {
    it.property("subnetId", subnetId)
  }

  /**
   * The VPC ID. This option is mandatory when creating an instance.
   */
  public fun vpcId(vpcId: String) {
    it.property("vpcId", vpcId)
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
}
