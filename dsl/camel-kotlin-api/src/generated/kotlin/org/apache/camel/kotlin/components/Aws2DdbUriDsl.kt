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
 * Store and retrieve data from AWS DynamoDB.
 */
public fun UriDsl.`aws2-ddb`(i: Aws2DdbUriDsl.() -> Unit) {
  Aws2DdbUriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2DdbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-ddb")
  }

  private var tableName: String = ""

  /**
   * The name of the table currently worked with.
   */
  public fun tableName(tableName: String) {
    this.tableName = tableName
    it.url("$tableName")
  }

  /**
   * Determines whether strong consistency should be enforced when data is read.
   */
  public fun consistentRead(consistentRead: String) {
    it.property("consistentRead", consistentRead)
  }

  /**
   * Determines whether strong consistency should be enforced when data is read.
   */
  public fun consistentRead(consistentRead: Boolean) {
    it.property("consistentRead", consistentRead.toString())
  }

  /**
   * Set whether the initial Describe table operation in the DDB Endpoint must be done, or not.
   */
  public fun enabledInitialDescribeTable(enabledInitialDescribeTable: String) {
    it.property("enabledInitialDescribeTable", enabledInitialDescribeTable)
  }

  /**
   * Set whether the initial Describe table operation in the DDB Endpoint must be done, or not.
   */
  public fun enabledInitialDescribeTable(enabledInitialDescribeTable: Boolean) {
    it.property("enabledInitialDescribeTable", enabledInitialDescribeTable.toString())
  }

  /**
   * Attribute name when creating table
   */
  public fun keyAttributeName(keyAttributeName: String) {
    it.property("keyAttributeName", keyAttributeName)
  }

  /**
   * Attribute type when creating table
   */
  public fun keyAttributeType(keyAttributeType: String) {
    it.property("keyAttributeType", keyAttributeType)
  }

  /**
   * The key scalar type, it can be S (String), N (Number) and B (Bytes)
   */
  public fun keyScalarType(keyScalarType: String) {
    it.property("keyScalarType", keyScalarType)
  }

  /**
   * What operation to perform
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  /**
   * The provisioned throughput to reserve for reading resources from your table
   */
  public fun readCapacity(readCapacity: String) {
    it.property("readCapacity", readCapacity)
  }

  /**
   * The provisioned throughput to reserve for reading resources from your table
   */
  public fun readCapacity(readCapacity: Int) {
    it.property("readCapacity", readCapacity.toString())
  }

  /**
   * The region in which DDB client needs to work
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
   * The provisioned throughput to reserved for writing resources to your table
   */
  public fun writeCapacity(writeCapacity: String) {
    it.property("writeCapacity", writeCapacity)
  }

  /**
   * The provisioned throughput to reserved for writing resources to your table
   */
  public fun writeCapacity(writeCapacity: Int) {
    it.property("writeCapacity", writeCapacity.toString())
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
   * To use the AmazonDynamoDB as the client
   */
  public fun amazonDDBClient(amazonDDBClient: String) {
    it.property("amazonDDBClient", amazonDDBClient)
  }

  /**
   * To define a proxy host when instantiating the DDB client
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * The region in which DynamoDB client needs to work. When using this parameter, the configuration
   * will expect the lowercase name of the region (for example ap-east-1) You'll need to use the name
   * Region.EU_WEST_1.id()
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * The region in which DynamoDB client needs to work. When using this parameter, the configuration
   * will expect the lowercase name of the region (for example ap-east-1) You'll need to use the name
   * Region.EU_WEST_1.id()
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the DDB client
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
   * If using a profile credentials provider this parameter will set the profile name
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
   * Amazon AWS Session Token used when the user needs to assume a IAM role
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
   * Set whether the S3 client should expect to load credentials through a default credentials
   * provider or to expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the S3 client should expect to load credentials through a default credentials
   * provider or to expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the DDB client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the DDB client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  /**
   * Set whether the DDB client should expect to use Session Credentials. This is useful in
   * situation in which the user needs to assume a IAM role for doing operations in DDB.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the DDB client should expect to use Session Credentials. This is useful in
   * situation in which the user needs to assume a IAM role for doing operations in DDB.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
