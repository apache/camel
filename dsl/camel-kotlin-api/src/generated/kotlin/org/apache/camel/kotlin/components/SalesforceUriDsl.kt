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

public fun UriDsl.salesforce(i: SalesforceUriDsl.() -> Unit) {
  SalesforceUriDsl(this).apply(i)
}

@CamelDslMarker
public class SalesforceUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("salesforce")
  }

  private var operationName: String = ""

  private var topicName: String = ""

  public fun operationName(operationName: String) {
    this.operationName = operationName
    it.url("$operationName:$topicName")
  }

  public fun topicName(topicName: String) {
    this.topicName = topicName
    it.url("$operationName:$topicName")
  }

  public fun apexMethod(apexMethod: String) {
    it.property("apexMethod", apexMethod)
  }

  public fun apexQueryParams(apexQueryParams: String) {
    it.property("apexQueryParams", apexQueryParams)
  }

  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  public fun backoffIncrement(backoffIncrement: String) {
    it.property("backoffIncrement", backoffIncrement)
  }

  public fun batchId(batchId: String) {
    it.property("batchId", batchId)
  }

  public fun contentType(contentType: String) {
    it.property("contentType", contentType)
  }

  public fun defaultReplayId(defaultReplayId: String) {
    it.property("defaultReplayId", defaultReplayId)
  }

  public fun defaultReplayId(defaultReplayId: Int) {
    it.property("defaultReplayId", defaultReplayId.toString())
  }

  public fun fallBackReplayId(fallBackReplayId: String) {
    it.property("fallBackReplayId", fallBackReplayId)
  }

  public fun fallBackReplayId(fallBackReplayId: Int) {
    it.property("fallBackReplayId", fallBackReplayId.toString())
  }

  public fun format(format: String) {
    it.property("format", format)
  }

  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  public fun includeDetails(includeDetails: String) {
    it.property("includeDetails", includeDetails)
  }

  public fun includeDetails(includeDetails: Boolean) {
    it.property("includeDetails", includeDetails.toString())
  }

  public fun initialReplayIdMap(initialReplayIdMap: String) {
    it.property("initialReplayIdMap", initialReplayIdMap)
  }

  public fun instanceId(instanceId: String) {
    it.property("instanceId", instanceId)
  }

  public fun jobId(jobId: String) {
    it.property("jobId", jobId)
  }

  public fun limit(limit: String) {
    it.property("limit", limit)
  }

  public fun limit(limit: Int) {
    it.property("limit", limit.toString())
  }

  public fun locator(locator: String) {
    it.property("locator", locator)
  }

  public fun maxBackoff(maxBackoff: String) {
    it.property("maxBackoff", maxBackoff)
  }

  public fun maxRecords(maxRecords: String) {
    it.property("maxRecords", maxRecords)
  }

  public fun maxRecords(maxRecords: Int) {
    it.property("maxRecords", maxRecords.toString())
  }

  public fun notFoundBehaviour(notFoundBehaviour: String) {
    it.property("notFoundBehaviour", notFoundBehaviour)
  }

  public fun notifyForFields(notifyForFields: String) {
    it.property("notifyForFields", notifyForFields)
  }

  public fun notifyForOperationCreate(notifyForOperationCreate: String) {
    it.property("notifyForOperationCreate", notifyForOperationCreate)
  }

  public fun notifyForOperationCreate(notifyForOperationCreate: Boolean) {
    it.property("notifyForOperationCreate", notifyForOperationCreate.toString())
  }

  public fun notifyForOperationDelete(notifyForOperationDelete: String) {
    it.property("notifyForOperationDelete", notifyForOperationDelete)
  }

  public fun notifyForOperationDelete(notifyForOperationDelete: Boolean) {
    it.property("notifyForOperationDelete", notifyForOperationDelete.toString())
  }

  public fun notifyForOperations(notifyForOperations: String) {
    it.property("notifyForOperations", notifyForOperations)
  }

  public fun notifyForOperationUndelete(notifyForOperationUndelete: String) {
    it.property("notifyForOperationUndelete", notifyForOperationUndelete)
  }

  public fun notifyForOperationUndelete(notifyForOperationUndelete: Boolean) {
    it.property("notifyForOperationUndelete", notifyForOperationUndelete.toString())
  }

  public fun notifyForOperationUpdate(notifyForOperationUpdate: String) {
    it.property("notifyForOperationUpdate", notifyForOperationUpdate)
  }

  public fun notifyForOperationUpdate(notifyForOperationUpdate: Boolean) {
    it.property("notifyForOperationUpdate", notifyForOperationUpdate.toString())
  }

  public fun objectMapper(objectMapper: String) {
    it.property("objectMapper", objectMapper)
  }

  public fun pkChunking(pkChunking: String) {
    it.property("pkChunking", pkChunking)
  }

  public fun pkChunking(pkChunking: Boolean) {
    it.property("pkChunking", pkChunking.toString())
  }

  public fun pkChunkingChunkSize(pkChunkingChunkSize: String) {
    it.property("pkChunkingChunkSize", pkChunkingChunkSize)
  }

  public fun pkChunkingChunkSize(pkChunkingChunkSize: Int) {
    it.property("pkChunkingChunkSize", pkChunkingChunkSize.toString())
  }

  public fun pkChunkingParent(pkChunkingParent: String) {
    it.property("pkChunkingParent", pkChunkingParent)
  }

  public fun pkChunkingStartRow(pkChunkingStartRow: String) {
    it.property("pkChunkingStartRow", pkChunkingStartRow)
  }

  public fun queryLocator(queryLocator: String) {
    it.property("queryLocator", queryLocator)
  }

  public fun rawPayload(rawPayload: String) {
    it.property("rawPayload", rawPayload)
  }

  public fun rawPayload(rawPayload: Boolean) {
    it.property("rawPayload", rawPayload.toString())
  }

  public fun reportId(reportId: String) {
    it.property("reportId", reportId)
  }

  public fun reportMetadata(reportMetadata: String) {
    it.property("reportMetadata", reportMetadata)
  }

  public fun resultId(resultId: String) {
    it.property("resultId", resultId)
  }

  public fun sObjectBlobFieldName(sObjectBlobFieldName: String) {
    it.property("sObjectBlobFieldName", sObjectBlobFieldName)
  }

  public fun sObjectClass(sObjectClass: String) {
    it.property("sObjectClass", sObjectClass)
  }

  public fun sObjectFields(sObjectFields: String) {
    it.property("sObjectFields", sObjectFields)
  }

  public fun sObjectId(sObjectId: String) {
    it.property("sObjectId", sObjectId)
  }

  public fun sObjectIdName(sObjectIdName: String) {
    it.property("sObjectIdName", sObjectIdName)
  }

  public fun sObjectIdValue(sObjectIdValue: String) {
    it.property("sObjectIdValue", sObjectIdValue)
  }

  public fun sObjectName(sObjectName: String) {
    it.property("sObjectName", sObjectName)
  }

  public fun sObjectQuery(sObjectQuery: String) {
    it.property("sObjectQuery", sObjectQuery)
  }

  public fun sObjectSearch(sObjectSearch: String) {
    it.property("sObjectSearch", sObjectSearch)
  }

  public fun streamQueryResult(streamQueryResult: String) {
    it.property("streamQueryResult", streamQueryResult)
  }

  public fun streamQueryResult(streamQueryResult: Boolean) {
    it.property("streamQueryResult", streamQueryResult.toString())
  }

  public fun updateTopic(updateTopic: String) {
    it.property("updateTopic", updateTopic)
  }

  public fun updateTopic(updateTopic: Boolean) {
    it.property("updateTopic", updateTopic.toString())
  }

  public fun pubSubBatchSize(pubSubBatchSize: String) {
    it.property("pubSubBatchSize", pubSubBatchSize)
  }

  public fun pubSubBatchSize(pubSubBatchSize: Int) {
    it.property("pubSubBatchSize", pubSubBatchSize.toString())
  }

  public fun pubSubDeserializeType(pubSubDeserializeType: String) {
    it.property("pubSubDeserializeType", pubSubDeserializeType)
  }

  public fun pubSubPojoClass(pubSubPojoClass: String) {
    it.property("pubSubPojoClass", pubSubPojoClass)
  }

  public fun pubSubReplayId(pubSubReplayId: String) {
    it.property("pubSubReplayId", pubSubReplayId)
  }

  public fun replayId(replayId: String) {
    it.property("replayId", replayId)
  }

  public fun replayId(replayId: Int) {
    it.property("replayId", replayId.toString())
  }

  public fun replayPreset(replayPreset: String) {
    it.property("replayPreset", replayPreset)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun allOrNone(allOrNone: String) {
    it.property("allOrNone", allOrNone)
  }

  public fun allOrNone(allOrNone: Boolean) {
    it.property("allOrNone", allOrNone.toString())
  }

  public fun apexUrl(apexUrl: String) {
    it.property("apexUrl", apexUrl)
  }

  public fun compositeMethod(compositeMethod: String) {
    it.property("compositeMethod", compositeMethod)
  }

  public fun eventName(eventName: String) {
    it.property("eventName", eventName)
  }

  public fun eventSchemaFormat(eventSchemaFormat: String) {
    it.property("eventSchemaFormat", eventSchemaFormat)
  }

  public fun eventSchemaId(eventSchemaId: String) {
    it.property("eventSchemaId", eventSchemaId)
  }

  public fun rawHttpHeaders(rawHttpHeaders: String) {
    it.property("rawHttpHeaders", rawHttpHeaders)
  }

  public fun rawMethod(rawMethod: String) {
    it.property("rawMethod", rawMethod)
  }

  public fun rawPath(rawPath: String) {
    it.property("rawPath", rawPath)
  }

  public fun rawQueryParameters(rawQueryParameters: String) {
    it.property("rawQueryParameters", rawQueryParameters)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
