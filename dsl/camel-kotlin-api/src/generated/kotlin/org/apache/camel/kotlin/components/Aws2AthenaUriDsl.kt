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

public fun UriDsl.`aws2-athena`(i: Aws2AthenaUriDsl.() -> Unit) {
  Aws2AthenaUriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2AthenaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-athena")
  }

  private var label: String = ""

  public fun label(label: String) {
    this.label = label
    it.url("$label")
  }

  public fun database(database: String) {
    it.property("database", database)
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  public fun maxAttempts(maxAttempts: String) {
    it.property("maxAttempts", maxAttempts)
  }

  public fun maxAttempts(maxAttempts: Int) {
    it.property("maxAttempts", maxAttempts.toString())
  }

  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  public fun nextToken(nextToken: String) {
    it.property("nextToken", nextToken)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun outputLocation(outputLocation: String) {
    it.property("outputLocation", outputLocation)
  }

  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  public fun queryExecutionId(queryExecutionId: String) {
    it.property("queryExecutionId", queryExecutionId)
  }

  public fun queryString(queryString: String) {
    it.property("queryString", queryString)
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun resetWaitTimeoutOnRetry(resetWaitTimeoutOnRetry: String) {
    it.property("resetWaitTimeoutOnRetry", resetWaitTimeoutOnRetry)
  }

  public fun resetWaitTimeoutOnRetry(resetWaitTimeoutOnRetry: Boolean) {
    it.property("resetWaitTimeoutOnRetry", resetWaitTimeoutOnRetry.toString())
  }

  public fun retry(retry: String) {
    it.property("retry", retry)
  }

  public fun waitTimeout(waitTimeout: String) {
    it.property("waitTimeout", waitTimeout)
  }

  public fun waitTimeout(waitTimeout: Int) {
    it.property("waitTimeout", waitTimeout.toString())
  }

  public fun workGroup(workGroup: String) {
    it.property("workGroup", workGroup)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun amazonAthenaClient(amazonAthenaClient: String) {
    it.property("amazonAthenaClient", amazonAthenaClient)
  }

  public fun clientRequestToken(clientRequestToken: String) {
    it.property("clientRequestToken", clientRequestToken)
  }

  public fun includeTrace(includeTrace: String) {
    it.property("includeTrace", includeTrace)
  }

  public fun includeTrace(includeTrace: Boolean) {
    it.property("includeTrace", includeTrace.toString())
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

  public fun encryptionOption(encryptionOption: String) {
    it.property("encryptionOption", encryptionOption)
  }

  public fun kmsKey(kmsKey: String) {
    it.property("kmsKey", kmsKey)
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
