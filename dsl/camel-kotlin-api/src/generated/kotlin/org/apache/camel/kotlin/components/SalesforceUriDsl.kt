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
 * Communicate with Salesforce using Java DTOs.
 */
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

  /**
   * The operation to use
   */
  public fun operationName(operationName: String) {
    this.operationName = operationName
    it.url("$operationName:$topicName")
  }

  /**
   * The name of the topic/channel to use
   */
  public fun topicName(topicName: String) {
    this.topicName = topicName
    it.url("$operationName:$topicName")
  }

  /**
   * APEX method name
   */
  public fun apexMethod(apexMethod: String) {
    it.property("apexMethod", apexMethod)
  }

  /**
   * Query params for APEX method
   */
  public fun apexQueryParams(apexQueryParams: String) {
    it.property("apexQueryParams", apexQueryParams)
  }

  /**
   * Salesforce API version.
   */
  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  /**
   * Backoff interval increment for Streaming connection restart attempts for failures beyond CometD
   * auto-reconnect.
   */
  public fun backoffIncrement(backoffIncrement: String) {
    it.property("backoffIncrement", backoffIncrement)
  }

  /**
   * Bulk API Batch ID
   */
  public fun batchId(batchId: String) {
    it.property("batchId", batchId)
  }

  /**
   * Bulk API content type, one of XML, CSV, ZIP_XML, ZIP_CSV
   */
  public fun contentType(contentType: String) {
    it.property("contentType", contentType)
  }

  /**
   * Default replayId setting if no value is found in initialReplayIdMap
   */
  public fun defaultReplayId(defaultReplayId: String) {
    it.property("defaultReplayId", defaultReplayId)
  }

  /**
   * Default replayId setting if no value is found in initialReplayIdMap
   */
  public fun defaultReplayId(defaultReplayId: Int) {
    it.property("defaultReplayId", defaultReplayId.toString())
  }

  /**
   * ReplayId to fall back to after an Invalid Replay Id response
   */
  public fun fallBackReplayId(fallBackReplayId: String) {
    it.property("fallBackReplayId", fallBackReplayId)
  }

  /**
   * ReplayId to fall back to after an Invalid Replay Id response
   */
  public fun fallBackReplayId(fallBackReplayId: Int) {
    it.property("fallBackReplayId", fallBackReplayId.toString())
  }

  /**
   * Payload format to use for Salesforce API calls, either JSON or XML, defaults to JSON. As of
   * Camel 3.12, this option only applies to the Raw operation.
   */
  public fun format(format: String) {
    it.property("format", format)
  }

  /**
   * Custom Jetty Http Client to use to connect to Salesforce.
   */
  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  /**
   * Include details in Salesforce1 Analytics report, defaults to false.
   */
  public fun includeDetails(includeDetails: String) {
    it.property("includeDetails", includeDetails)
  }

  /**
   * Include details in Salesforce1 Analytics report, defaults to false.
   */
  public fun includeDetails(includeDetails: Boolean) {
    it.property("includeDetails", includeDetails.toString())
  }

  /**
   * Replay IDs to start from per channel name.
   */
  public fun initialReplayIdMap(initialReplayIdMap: String) {
    it.property("initialReplayIdMap", initialReplayIdMap)
  }

  /**
   * Salesforce1 Analytics report execution instance ID
   */
  public fun instanceId(instanceId: String) {
    it.property("instanceId", instanceId)
  }

  /**
   * Bulk API Job ID
   */
  public fun jobId(jobId: String) {
    it.property("jobId", jobId)
  }

  /**
   * Limit on number of returned records. Applicable to some of the API, check the Salesforce
   * documentation.
   */
  public fun limit(limit: String) {
    it.property("limit", limit)
  }

  /**
   * Limit on number of returned records. Applicable to some of the API, check the Salesforce
   * documentation.
   */
  public fun limit(limit: Int) {
    it.property("limit", limit.toString())
  }

  /**
   * Locator provided by salesforce Bulk 2.0 API for use in getting results for a Query job.
   */
  public fun locator(locator: String) {
    it.property("locator", locator)
  }

  /**
   * Maximum backoff interval for Streaming connection restart attempts for failures beyond CometD
   * auto-reconnect.
   */
  public fun maxBackoff(maxBackoff: String) {
    it.property("maxBackoff", maxBackoff)
  }

  /**
   * The maximum number of records to retrieve per set of results for a Bulk 2.0 Query. The request
   * is still subject to the size limits. If you are working with a very large number of query results,
   * you may experience a timeout before receiving all the data from Salesforce. To prevent a timeout,
   * specify the maximum number of records your client is expecting to receive in the maxRecords
   * parameter. This splits the results into smaller sets with this value as the maximum size.
   */
  public fun maxRecords(maxRecords: String) {
    it.property("maxRecords", maxRecords)
  }

  /**
   * The maximum number of records to retrieve per set of results for a Bulk 2.0 Query. The request
   * is still subject to the size limits. If you are working with a very large number of query results,
   * you may experience a timeout before receiving all the data from Salesforce. To prevent a timeout,
   * specify the maximum number of records your client is expecting to receive in the maxRecords
   * parameter. This splits the results into smaller sets with this value as the maximum size.
   */
  public fun maxRecords(maxRecords: Int) {
    it.property("maxRecords", maxRecords.toString())
  }

  /**
   * Sets the behaviour of 404 not found status received from Salesforce API. Should the body be set
   * to NULL NotFoundBehaviour#NULL or should a exception be signaled on the exchange
   * NotFoundBehaviour#EXCEPTION - the default.
   */
  public fun notFoundBehaviour(notFoundBehaviour: String) {
    it.property("notFoundBehaviour", notFoundBehaviour)
  }

  /**
   * Notify for fields, options are ALL, REFERENCED, SELECT, WHERE
   */
  public fun notifyForFields(notifyForFields: String) {
    it.property("notifyForFields", notifyForFields)
  }

  /**
   * Notify for create operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationCreate(notifyForOperationCreate: String) {
    it.property("notifyForOperationCreate", notifyForOperationCreate)
  }

  /**
   * Notify for create operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationCreate(notifyForOperationCreate: Boolean) {
    it.property("notifyForOperationCreate", notifyForOperationCreate.toString())
  }

  /**
   * Notify for delete operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationDelete(notifyForOperationDelete: String) {
    it.property("notifyForOperationDelete", notifyForOperationDelete)
  }

  /**
   * Notify for delete operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationDelete(notifyForOperationDelete: Boolean) {
    it.property("notifyForOperationDelete", notifyForOperationDelete.toString())
  }

  /**
   * Notify for operations, options are ALL, CREATE, EXTENDED, UPDATE (API version &lt; 29.0)
   */
  public fun notifyForOperations(notifyForOperations: String) {
    it.property("notifyForOperations", notifyForOperations)
  }

  /**
   * Notify for un-delete operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationUndelete(notifyForOperationUndelete: String) {
    it.property("notifyForOperationUndelete", notifyForOperationUndelete)
  }

  /**
   * Notify for un-delete operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationUndelete(notifyForOperationUndelete: Boolean) {
    it.property("notifyForOperationUndelete", notifyForOperationUndelete.toString())
  }

  /**
   * Notify for update operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationUpdate(notifyForOperationUpdate: String) {
    it.property("notifyForOperationUpdate", notifyForOperationUpdate)
  }

  /**
   * Notify for update operation, defaults to false (API version &gt;= 29.0)
   */
  public fun notifyForOperationUpdate(notifyForOperationUpdate: Boolean) {
    it.property("notifyForOperationUpdate", notifyForOperationUpdate.toString())
  }

  /**
   * Custom Jackson ObjectMapper to use when serializing/deserializing Salesforce objects.
   */
  public fun objectMapper(objectMapper: String) {
    it.property("objectMapper", objectMapper)
  }

  /**
   * Use PK Chunking. Only for use in original Bulk API. Bulk 2.0 API performs PK chunking
   * automatically, if necessary.
   */
  public fun pkChunking(pkChunking: String) {
    it.property("pkChunking", pkChunking)
  }

  /**
   * Use PK Chunking. Only for use in original Bulk API. Bulk 2.0 API performs PK chunking
   * automatically, if necessary.
   */
  public fun pkChunking(pkChunking: Boolean) {
    it.property("pkChunking", pkChunking.toString())
  }

  /**
   * Chunk size for use with PK Chunking. If unspecified, salesforce default is 100,000. Maximum
   * size is 250,000.
   */
  public fun pkChunkingChunkSize(pkChunkingChunkSize: String) {
    it.property("pkChunkingChunkSize", pkChunkingChunkSize)
  }

  /**
   * Chunk size for use with PK Chunking. If unspecified, salesforce default is 100,000. Maximum
   * size is 250,000.
   */
  public fun pkChunkingChunkSize(pkChunkingChunkSize: Int) {
    it.property("pkChunkingChunkSize", pkChunkingChunkSize.toString())
  }

  /**
   * Specifies the parent object when you're enabling PK chunking for queries on sharing objects.
   * The chunks are based on the parent object's records rather than the sharing object's records. For
   * example, when querying on AccountShare, specify Account as the parent object. PK chunking is
   * supported for sharing objects as long as the parent object is supported.
   */
  public fun pkChunkingParent(pkChunkingParent: String) {
    it.property("pkChunkingParent", pkChunkingParent)
  }

  /**
   * Specifies the 15-character or 18-character record ID to be used as the lower boundary for the
   * first chunk. Use this parameter to specify a starting ID when restarting a job that failed between
   * batches.
   */
  public fun pkChunkingStartRow(pkChunkingStartRow: String) {
    it.property("pkChunkingStartRow", pkChunkingStartRow)
  }

  /**
   * Query Locator provided by salesforce for use when a query results in more records than can be
   * retrieved in a single call. Use this value in a subsequent call to retrieve additional records.
   */
  public fun queryLocator(queryLocator: String) {
    it.property("queryLocator", queryLocator)
  }

  /**
   * Use raw payload String for request and response (either JSON or XML depending on format),
   * instead of DTOs, false by default
   */
  public fun rawPayload(rawPayload: String) {
    it.property("rawPayload", rawPayload)
  }

  /**
   * Use raw payload String for request and response (either JSON or XML depending on format),
   * instead of DTOs, false by default
   */
  public fun rawPayload(rawPayload: Boolean) {
    it.property("rawPayload", rawPayload.toString())
  }

  /**
   * Salesforce1 Analytics report Id
   */
  public fun reportId(reportId: String) {
    it.property("reportId", reportId)
  }

  /**
   * Salesforce1 Analytics report metadata for filtering
   */
  public fun reportMetadata(reportMetadata: String) {
    it.property("reportMetadata", reportMetadata)
  }

  /**
   * Bulk API Result ID
   */
  public fun resultId(resultId: String) {
    it.property("resultId", resultId)
  }

  /**
   * SObject blob field name
   */
  public fun sObjectBlobFieldName(sObjectBlobFieldName: String) {
    it.property("sObjectBlobFieldName", sObjectBlobFieldName)
  }

  /**
   * Fully qualified SObject class name, usually generated using camel-salesforce-maven-plugin
   */
  public fun sObjectClass(sObjectClass: String) {
    it.property("sObjectClass", sObjectClass)
  }

  /**
   * SObject fields to retrieve
   */
  public fun sObjectFields(sObjectFields: String) {
    it.property("sObjectFields", sObjectFields)
  }

  /**
   * SObject ID if required by API
   */
  public fun sObjectId(sObjectId: String) {
    it.property("sObjectId", sObjectId)
  }

  /**
   * SObject external ID field name
   */
  public fun sObjectIdName(sObjectIdName: String) {
    it.property("sObjectIdName", sObjectIdName)
  }

  /**
   * SObject external ID field value
   */
  public fun sObjectIdValue(sObjectIdValue: String) {
    it.property("sObjectIdValue", sObjectIdValue)
  }

  /**
   * SObject name if required or supported by API
   */
  public fun sObjectName(sObjectName: String) {
    it.property("sObjectName", sObjectName)
  }

  /**
   * Salesforce SOQL query string
   */
  public fun sObjectQuery(sObjectQuery: String) {
    it.property("sObjectQuery", sObjectQuery)
  }

  /**
   * Salesforce SOSL search string
   */
  public fun sObjectSearch(sObjectSearch: String) {
    it.property("sObjectSearch", sObjectSearch)
  }

  /**
   * If true, streams SOQL query result and transparently handles subsequent requests if there are
   * multiple pages. Otherwise, results are returned one page at a time.
   */
  public fun streamQueryResult(streamQueryResult: String) {
    it.property("streamQueryResult", streamQueryResult)
  }

  /**
   * If true, streams SOQL query result and transparently handles subsequent requests if there are
   * multiple pages. Otherwise, results are returned one page at a time.
   */
  public fun streamQueryResult(streamQueryResult: Boolean) {
    it.property("streamQueryResult", streamQueryResult.toString())
  }

  /**
   * Whether to update an existing Push Topic when using the Streaming API, defaults to false
   */
  public fun updateTopic(updateTopic: String) {
    it.property("updateTopic", updateTopic)
  }

  /**
   * Whether to update an existing Push Topic when using the Streaming API, defaults to false
   */
  public fun updateTopic(updateTopic: Boolean) {
    it.property("updateTopic", updateTopic.toString())
  }

  /**
   * Max number of events to receive in a batch from the Pub/Sub API.
   */
  public fun pubSubBatchSize(pubSubBatchSize: String) {
    it.property("pubSubBatchSize", pubSubBatchSize)
  }

  /**
   * Max number of events to receive in a batch from the Pub/Sub API.
   */
  public fun pubSubBatchSize(pubSubBatchSize: Int) {
    it.property("pubSubBatchSize", pubSubBatchSize.toString())
  }

  /**
   * How to deserialize events consume from the Pub/Sub API. AVRO will try a SpecificRecord subclass
   * if found, otherwise GenericRecord.
   */
  public fun pubSubDeserializeType(pubSubDeserializeType: String) {
    it.property("pubSubDeserializeType", pubSubDeserializeType)
  }

  /**
   * Fully qualified class name to deserialize Pub/Sub API event to.
   */
  public fun pubSubPojoClass(pubSubPojoClass: String) {
    it.property("pubSubPojoClass", pubSubPojoClass)
  }

  /**
   * The replayId value to use when subscribing to the Pub/Sub API.
   */
  public fun pubSubReplayId(pubSubReplayId: String) {
    it.property("pubSubReplayId", pubSubReplayId)
  }

  /**
   * The replayId value to use when subscribing to the Streaming API.
   */
  public fun replayId(replayId: String) {
    it.property("replayId", replayId)
  }

  /**
   * The replayId value to use when subscribing to the Streaming API.
   */
  public fun replayId(replayId: Int) {
    it.property("replayId", replayId.toString())
  }

  /**
   * Replay preset for Pub/Sub API.
   */
  public fun replayPreset(replayPreset: String) {
    it.property("replayPreset", replayPreset)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * Composite API option to indicate to rollback all records if any are not successful.
   */
  public fun allOrNone(allOrNone: String) {
    it.property("allOrNone", allOrNone)
  }

  /**
   * Composite API option to indicate to rollback all records if any are not successful.
   */
  public fun allOrNone(allOrNone: Boolean) {
    it.property("allOrNone", allOrNone.toString())
  }

  /**
   * APEX method URL
   */
  public fun apexUrl(apexUrl: String) {
    it.property("apexUrl", apexUrl)
  }

  /**
   * Composite (raw) method.
   */
  public fun compositeMethod(compositeMethod: String) {
    it.property("compositeMethod", compositeMethod)
  }

  /**
   * Name of Platform Event, Change Data Capture Event, custom event, etc.
   */
  public fun eventName(eventName: String) {
    it.property("eventName", eventName)
  }

  /**
   * EXPANDED: Apache Avro format but doesn't strictly adhere to the record complex type. COMPACT:
   * Apache Avro, adheres to the specification for the record complex type. This parameter is available
   * in API version 43.0 and later.
   */
  public fun eventSchemaFormat(eventSchemaFormat: String) {
    it.property("eventSchemaFormat", eventSchemaFormat)
  }

  /**
   * The ID of the event schema.
   */
  public fun eventSchemaId(eventSchemaId: String) {
    it.property("eventSchemaId", eventSchemaId)
  }

  /**
   * Comma separated list of message headers to include as HTTP parameters for Raw operation.
   */
  public fun rawHttpHeaders(rawHttpHeaders: String) {
    it.property("rawHttpHeaders", rawHttpHeaders)
  }

  /**
   * HTTP method to use for the Raw operation
   */
  public fun rawMethod(rawMethod: String) {
    it.property("rawMethod", rawMethod)
  }

  /**
   * The portion of the endpoint URL after the domain name. E.g.,
   * '/services/data/v52.0/sobjects/Account/'
   */
  public fun rawPath(rawPath: String) {
    it.property("rawPath", rawPath)
  }

  /**
   * Comma separated list of message headers to include as query parameters for Raw operation. Do
   * not url-encode values as this will be done automatically.
   */
  public fun rawQueryParameters(rawQueryParameters: String) {
    it.property("rawQueryParameters", rawQueryParameters)
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
