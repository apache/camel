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

  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  public fun accessUser(accessUser: String) {
    it.property("accessUser", accessUser)
  }

  public fun availableZones(availableZones: String) {
    it.property("availableZones", availableZones)
  }

  public fun endpoint(endpoint: String) {
    it.property("endpoint", endpoint)
  }

  public fun engine(engine: String) {
    it.property("engine", engine)
  }

  public fun engineVersion(engineVersion: String) {
    it.property("engineVersion", engineVersion)
  }

  public fun ignoreSslVerification(ignoreSslVerification: String) {
    it.property("ignoreSslVerification", ignoreSslVerification)
  }

  public fun ignoreSslVerification(ignoreSslVerification: Boolean) {
    it.property("ignoreSslVerification", ignoreSslVerification.toString())
  }

  public fun instanceId(instanceId: String) {
    it.property("instanceId", instanceId)
  }

  public fun kafkaManagerPassword(kafkaManagerPassword: String) {
    it.property("kafkaManagerPassword", kafkaManagerPassword)
  }

  public fun kafkaManagerUser(kafkaManagerUser: String) {
    it.property("kafkaManagerUser", kafkaManagerUser)
  }

  public fun name(name: String) {
    it.property("name", name)
  }

  public fun partitionNum(partitionNum: String) {
    it.property("partitionNum", partitionNum)
  }

  public fun partitionNum(partitionNum: Int) {
    it.property("partitionNum", partitionNum.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun productId(productId: String) {
    it.property("productId", productId)
  }

  public fun projectId(projectId: String) {
    it.property("projectId", projectId)
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun proxyUser(proxyUser: String) {
    it.property("proxyUser", proxyUser)
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  public fun securityGroupId(securityGroupId: String) {
    it.property("securityGroupId", securityGroupId)
  }

  public fun serviceKeys(serviceKeys: String) {
    it.property("serviceKeys", serviceKeys)
  }

  public fun specification(specification: String) {
    it.property("specification", specification)
  }

  public fun storageSpace(storageSpace: String) {
    it.property("storageSpace", storageSpace)
  }

  public fun storageSpace(storageSpace: Int) {
    it.property("storageSpace", storageSpace.toString())
  }

  public fun storageSpecCode(storageSpecCode: String) {
    it.property("storageSpecCode", storageSpecCode)
  }

  public fun subnetId(subnetId: String) {
    it.property("subnetId", subnetId)
  }

  public fun vpcId(vpcId: String) {
    it.property("vpcId", vpcId)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
