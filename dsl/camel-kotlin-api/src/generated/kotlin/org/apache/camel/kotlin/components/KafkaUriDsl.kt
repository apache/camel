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

public fun UriDsl.kafka(i: KafkaUriDsl.() -> Unit) {
  KafkaUriDsl(this).apply(i)
}

@CamelDslMarker
public class KafkaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("kafka")
  }

  private var topic: String = ""

  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  public fun additionalProperties(additionalProperties: String) {
    it.property("additionalProperties", additionalProperties)
  }

  public fun brokers(brokers: String) {
    it.property("brokers", brokers)
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun reconnectBackoffMaxMs(reconnectBackoffMaxMs: String) {
    it.property("reconnectBackoffMaxMs", reconnectBackoffMaxMs)
  }

  public fun reconnectBackoffMaxMs(reconnectBackoffMaxMs: Int) {
    it.property("reconnectBackoffMaxMs", reconnectBackoffMaxMs.toString())
  }

  public fun shutdownTimeout(shutdownTimeout: String) {
    it.property("shutdownTimeout", shutdownTimeout)
  }

  public fun shutdownTimeout(shutdownTimeout: Int) {
    it.property("shutdownTimeout", shutdownTimeout.toString())
  }

  public fun allowManualCommit(allowManualCommit: String) {
    it.property("allowManualCommit", allowManualCommit)
  }

  public fun allowManualCommit(allowManualCommit: Boolean) {
    it.property("allowManualCommit", allowManualCommit.toString())
  }

  public fun autoCommitEnable(autoCommitEnable: String) {
    it.property("autoCommitEnable", autoCommitEnable)
  }

  public fun autoCommitEnable(autoCommitEnable: Boolean) {
    it.property("autoCommitEnable", autoCommitEnable.toString())
  }

  public fun autoCommitIntervalMs(autoCommitIntervalMs: String) {
    it.property("autoCommitIntervalMs", autoCommitIntervalMs)
  }

  public fun autoCommitIntervalMs(autoCommitIntervalMs: Int) {
    it.property("autoCommitIntervalMs", autoCommitIntervalMs.toString())
  }

  public fun autoOffsetReset(autoOffsetReset: String) {
    it.property("autoOffsetReset", autoOffsetReset)
  }

  public fun batching(batching: String) {
    it.property("batching", batching)
  }

  public fun batching(batching: Boolean) {
    it.property("batching", batching.toString())
  }

  public fun breakOnFirstError(breakOnFirstError: String) {
    it.property("breakOnFirstError", breakOnFirstError)
  }

  public fun breakOnFirstError(breakOnFirstError: Boolean) {
    it.property("breakOnFirstError", breakOnFirstError.toString())
  }

  public fun checkCrcs(checkCrcs: String) {
    it.property("checkCrcs", checkCrcs)
  }

  public fun checkCrcs(checkCrcs: Boolean) {
    it.property("checkCrcs", checkCrcs.toString())
  }

  public fun commitTimeoutMs(commitTimeoutMs: String) {
    it.property("commitTimeoutMs", commitTimeoutMs)
  }

  public fun consumerRequestTimeoutMs(consumerRequestTimeoutMs: String) {
    it.property("consumerRequestTimeoutMs", consumerRequestTimeoutMs)
  }

  public fun consumerRequestTimeoutMs(consumerRequestTimeoutMs: Int) {
    it.property("consumerRequestTimeoutMs", consumerRequestTimeoutMs.toString())
  }

  public fun consumersCount(consumersCount: String) {
    it.property("consumersCount", consumersCount)
  }

  public fun consumersCount(consumersCount: Int) {
    it.property("consumersCount", consumersCount.toString())
  }

  public fun fetchMaxBytes(fetchMaxBytes: String) {
    it.property("fetchMaxBytes", fetchMaxBytes)
  }

  public fun fetchMaxBytes(fetchMaxBytes: Int) {
    it.property("fetchMaxBytes", fetchMaxBytes.toString())
  }

  public fun fetchMinBytes(fetchMinBytes: String) {
    it.property("fetchMinBytes", fetchMinBytes)
  }

  public fun fetchMinBytes(fetchMinBytes: Int) {
    it.property("fetchMinBytes", fetchMinBytes.toString())
  }

  public fun fetchWaitMaxMs(fetchWaitMaxMs: String) {
    it.property("fetchWaitMaxMs", fetchWaitMaxMs)
  }

  public fun fetchWaitMaxMs(fetchWaitMaxMs: Int) {
    it.property("fetchWaitMaxMs", fetchWaitMaxMs.toString())
  }

  public fun groupId(groupId: String) {
    it.property("groupId", groupId)
  }

  public fun groupInstanceId(groupInstanceId: String) {
    it.property("groupInstanceId", groupInstanceId)
  }

  public fun headerDeserializer(headerDeserializer: String) {
    it.property("headerDeserializer", headerDeserializer)
  }

  public fun heartbeatIntervalMs(heartbeatIntervalMs: String) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs)
  }

  public fun heartbeatIntervalMs(heartbeatIntervalMs: Int) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs.toString())
  }

  public fun keyDeserializer(keyDeserializer: String) {
    it.property("keyDeserializer", keyDeserializer)
  }

  public fun maxPartitionFetchBytes(maxPartitionFetchBytes: String) {
    it.property("maxPartitionFetchBytes", maxPartitionFetchBytes)
  }

  public fun maxPartitionFetchBytes(maxPartitionFetchBytes: Int) {
    it.property("maxPartitionFetchBytes", maxPartitionFetchBytes.toString())
  }

  public fun maxPollIntervalMs(maxPollIntervalMs: String) {
    it.property("maxPollIntervalMs", maxPollIntervalMs)
  }

  public fun maxPollRecords(maxPollRecords: String) {
    it.property("maxPollRecords", maxPollRecords)
  }

  public fun maxPollRecords(maxPollRecords: Int) {
    it.property("maxPollRecords", maxPollRecords.toString())
  }

  public fun offsetRepository(offsetRepository: String) {
    it.property("offsetRepository", offsetRepository)
  }

  public fun partitionAssignor(partitionAssignor: String) {
    it.property("partitionAssignor", partitionAssignor)
  }

  public fun pollOnError(pollOnError: String) {
    it.property("pollOnError", pollOnError)
  }

  public fun pollTimeoutMs(pollTimeoutMs: String) {
    it.property("pollTimeoutMs", pollTimeoutMs)
  }

  public fun preValidateHostAndPort(preValidateHostAndPort: String) {
    it.property("preValidateHostAndPort", preValidateHostAndPort)
  }

  public fun preValidateHostAndPort(preValidateHostAndPort: Boolean) {
    it.property("preValidateHostAndPort", preValidateHostAndPort.toString())
  }

  public fun seekTo(seekTo: String) {
    it.property("seekTo", seekTo)
  }

  public fun sessionTimeoutMs(sessionTimeoutMs: String) {
    it.property("sessionTimeoutMs", sessionTimeoutMs)
  }

  public fun sessionTimeoutMs(sessionTimeoutMs: Int) {
    it.property("sessionTimeoutMs", sessionTimeoutMs.toString())
  }

  public fun specificAvroReader(specificAvroReader: String) {
    it.property("specificAvroReader", specificAvroReader)
  }

  public fun specificAvroReader(specificAvroReader: Boolean) {
    it.property("specificAvroReader", specificAvroReader.toString())
  }

  public fun topicIsPattern(topicIsPattern: String) {
    it.property("topicIsPattern", topicIsPattern)
  }

  public fun topicIsPattern(topicIsPattern: Boolean) {
    it.property("topicIsPattern", topicIsPattern.toString())
  }

  public fun valueDeserializer(valueDeserializer: String) {
    it.property("valueDeserializer", valueDeserializer)
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

  public fun isolationLevel(isolationLevel: String) {
    it.property("isolationLevel", isolationLevel)
  }

  public fun kafkaManualCommitFactory(kafkaManualCommitFactory: String) {
    it.property("kafkaManualCommitFactory", kafkaManualCommitFactory)
  }

  public fun batchWithIndividualHeaders(batchWithIndividualHeaders: String) {
    it.property("batchWithIndividualHeaders", batchWithIndividualHeaders)
  }

  public fun batchWithIndividualHeaders(batchWithIndividualHeaders: Boolean) {
    it.property("batchWithIndividualHeaders", batchWithIndividualHeaders.toString())
  }

  public fun bufferMemorySize(bufferMemorySize: String) {
    it.property("bufferMemorySize", bufferMemorySize)
  }

  public fun bufferMemorySize(bufferMemorySize: Int) {
    it.property("bufferMemorySize", bufferMemorySize.toString())
  }

  public fun compressionCodec(compressionCodec: String) {
    it.property("compressionCodec", compressionCodec)
  }

  public fun connectionMaxIdleMs(connectionMaxIdleMs: String) {
    it.property("connectionMaxIdleMs", connectionMaxIdleMs)
  }

  public fun connectionMaxIdleMs(connectionMaxIdleMs: Int) {
    it.property("connectionMaxIdleMs", connectionMaxIdleMs.toString())
  }

  public fun deliveryTimeoutMs(deliveryTimeoutMs: String) {
    it.property("deliveryTimeoutMs", deliveryTimeoutMs)
  }

  public fun deliveryTimeoutMs(deliveryTimeoutMs: Int) {
    it.property("deliveryTimeoutMs", deliveryTimeoutMs.toString())
  }

  public fun enableIdempotence(enableIdempotence: String) {
    it.property("enableIdempotence", enableIdempotence)
  }

  public fun enableIdempotence(enableIdempotence: Boolean) {
    it.property("enableIdempotence", enableIdempotence.toString())
  }

  public fun headerSerializer(headerSerializer: String) {
    it.property("headerSerializer", headerSerializer)
  }

  public fun key(key: String) {
    it.property("key", key)
  }

  public fun keySerializer(keySerializer: String) {
    it.property("keySerializer", keySerializer)
  }

  public fun lingerMs(lingerMs: String) {
    it.property("lingerMs", lingerMs)
  }

  public fun lingerMs(lingerMs: Int) {
    it.property("lingerMs", lingerMs.toString())
  }

  public fun maxBlockMs(maxBlockMs: String) {
    it.property("maxBlockMs", maxBlockMs)
  }

  public fun maxBlockMs(maxBlockMs: Int) {
    it.property("maxBlockMs", maxBlockMs.toString())
  }

  public fun maxInFlightRequest(maxInFlightRequest: String) {
    it.property("maxInFlightRequest", maxInFlightRequest)
  }

  public fun maxInFlightRequest(maxInFlightRequest: Int) {
    it.property("maxInFlightRequest", maxInFlightRequest.toString())
  }

  public fun maxRequestSize(maxRequestSize: String) {
    it.property("maxRequestSize", maxRequestSize)
  }

  public fun maxRequestSize(maxRequestSize: Int) {
    it.property("maxRequestSize", maxRequestSize.toString())
  }

  public fun metadataMaxAgeMs(metadataMaxAgeMs: String) {
    it.property("metadataMaxAgeMs", metadataMaxAgeMs)
  }

  public fun metadataMaxAgeMs(metadataMaxAgeMs: Int) {
    it.property("metadataMaxAgeMs", metadataMaxAgeMs.toString())
  }

  public fun metricReporters(metricReporters: String) {
    it.property("metricReporters", metricReporters)
  }

  public fun metricsSampleWindowMs(metricsSampleWindowMs: String) {
    it.property("metricsSampleWindowMs", metricsSampleWindowMs)
  }

  public fun metricsSampleWindowMs(metricsSampleWindowMs: Int) {
    it.property("metricsSampleWindowMs", metricsSampleWindowMs.toString())
  }

  public fun noOfMetricsSample(noOfMetricsSample: String) {
    it.property("noOfMetricsSample", noOfMetricsSample)
  }

  public fun noOfMetricsSample(noOfMetricsSample: Int) {
    it.property("noOfMetricsSample", noOfMetricsSample.toString())
  }

  public fun partitioner(partitioner: String) {
    it.property("partitioner", partitioner)
  }

  public fun partitionerIgnoreKeys(partitionerIgnoreKeys: String) {
    it.property("partitionerIgnoreKeys", partitionerIgnoreKeys)
  }

  public fun partitionerIgnoreKeys(partitionerIgnoreKeys: Boolean) {
    it.property("partitionerIgnoreKeys", partitionerIgnoreKeys.toString())
  }

  public fun partitionKey(partitionKey: String) {
    it.property("partitionKey", partitionKey)
  }

  public fun partitionKey(partitionKey: Int) {
    it.property("partitionKey", partitionKey.toString())
  }

  public fun producerBatchSize(producerBatchSize: String) {
    it.property("producerBatchSize", producerBatchSize)
  }

  public fun producerBatchSize(producerBatchSize: Int) {
    it.property("producerBatchSize", producerBatchSize.toString())
  }

  public fun queueBufferingMaxMessages(queueBufferingMaxMessages: String) {
    it.property("queueBufferingMaxMessages", queueBufferingMaxMessages)
  }

  public fun queueBufferingMaxMessages(queueBufferingMaxMessages: Int) {
    it.property("queueBufferingMaxMessages", queueBufferingMaxMessages.toString())
  }

  public fun receiveBufferBytes(receiveBufferBytes: String) {
    it.property("receiveBufferBytes", receiveBufferBytes)
  }

  public fun receiveBufferBytes(receiveBufferBytes: Int) {
    it.property("receiveBufferBytes", receiveBufferBytes.toString())
  }

  public fun reconnectBackoffMs(reconnectBackoffMs: String) {
    it.property("reconnectBackoffMs", reconnectBackoffMs)
  }

  public fun reconnectBackoffMs(reconnectBackoffMs: Int) {
    it.property("reconnectBackoffMs", reconnectBackoffMs.toString())
  }

  public fun recordMetadata(recordMetadata: String) {
    it.property("recordMetadata", recordMetadata)
  }

  public fun recordMetadata(recordMetadata: Boolean) {
    it.property("recordMetadata", recordMetadata.toString())
  }

  public fun requestRequiredAcks(requestRequiredAcks: String) {
    it.property("requestRequiredAcks", requestRequiredAcks)
  }

  public fun requestTimeoutMs(requestTimeoutMs: String) {
    it.property("requestTimeoutMs", requestTimeoutMs)
  }

  public fun requestTimeoutMs(requestTimeoutMs: Int) {
    it.property("requestTimeoutMs", requestTimeoutMs.toString())
  }

  public fun retries(retries: String) {
    it.property("retries", retries)
  }

  public fun retries(retries: Int) {
    it.property("retries", retries.toString())
  }

  public fun retryBackoffMs(retryBackoffMs: String) {
    it.property("retryBackoffMs", retryBackoffMs)
  }

  public fun retryBackoffMs(retryBackoffMs: Int) {
    it.property("retryBackoffMs", retryBackoffMs.toString())
  }

  public fun sendBufferBytes(sendBufferBytes: String) {
    it.property("sendBufferBytes", sendBufferBytes)
  }

  public fun sendBufferBytes(sendBufferBytes: Int) {
    it.property("sendBufferBytes", sendBufferBytes.toString())
  }

  public fun valueSerializer(valueSerializer: String) {
    it.property("valueSerializer", valueSerializer)
  }

  public fun workerPool(workerPool: String) {
    it.property("workerPool", workerPool)
  }

  public fun workerPoolCoreSize(workerPoolCoreSize: String) {
    it.property("workerPoolCoreSize", workerPoolCoreSize)
  }

  public fun workerPoolCoreSize(workerPoolCoreSize: Int) {
    it.property("workerPoolCoreSize", workerPoolCoreSize.toString())
  }

  public fun workerPoolMaxSize(workerPoolMaxSize: String) {
    it.property("workerPoolMaxSize", workerPoolMaxSize)
  }

  public fun workerPoolMaxSize(workerPoolMaxSize: Int) {
    it.property("workerPoolMaxSize", workerPoolMaxSize.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun kafkaClientFactory(kafkaClientFactory: String) {
    it.property("kafkaClientFactory", kafkaClientFactory)
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun interceptorClasses(interceptorClasses: String) {
    it.property("interceptorClasses", interceptorClasses)
  }

  public fun schemaRegistryURL(schemaRegistryURL: String) {
    it.property("schemaRegistryURL", schemaRegistryURL)
  }

  public fun kerberosBeforeReloginMinTime(kerberosBeforeReloginMinTime: String) {
    it.property("kerberosBeforeReloginMinTime", kerberosBeforeReloginMinTime)
  }

  public fun kerberosBeforeReloginMinTime(kerberosBeforeReloginMinTime: Int) {
    it.property("kerberosBeforeReloginMinTime", kerberosBeforeReloginMinTime.toString())
  }

  public fun kerberosConfigLocation(kerberosConfigLocation: String) {
    it.property("kerberosConfigLocation", kerberosConfigLocation)
  }

  public fun kerberosInitCmd(kerberosInitCmd: String) {
    it.property("kerberosInitCmd", kerberosInitCmd)
  }

  public fun kerberosPrincipalToLocalRules(kerberosPrincipalToLocalRules: String) {
    it.property("kerberosPrincipalToLocalRules", kerberosPrincipalToLocalRules)
  }

  public fun kerberosRenewJitter(kerberosRenewJitter: String) {
    it.property("kerberosRenewJitter", kerberosRenewJitter)
  }

  public fun kerberosRenewJitter(kerberosRenewJitter: Double) {
    it.property("kerberosRenewJitter", kerberosRenewJitter.toString())
  }

  public fun kerberosRenewWindowFactor(kerberosRenewWindowFactor: String) {
    it.property("kerberosRenewWindowFactor", kerberosRenewWindowFactor)
  }

  public fun kerberosRenewWindowFactor(kerberosRenewWindowFactor: Double) {
    it.property("kerberosRenewWindowFactor", kerberosRenewWindowFactor.toString())
  }

  public fun saslJaasConfig(saslJaasConfig: String) {
    it.property("saslJaasConfig", saslJaasConfig)
  }

  public fun saslKerberosServiceName(saslKerberosServiceName: String) {
    it.property("saslKerberosServiceName", saslKerberosServiceName)
  }

  public fun saslMechanism(saslMechanism: String) {
    it.property("saslMechanism", saslMechanism)
  }

  public fun securityProtocol(securityProtocol: String) {
    it.property("securityProtocol", securityProtocol)
  }

  public fun sslCipherSuites(sslCipherSuites: String) {
    it.property("sslCipherSuites", sslCipherSuites)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun sslEnabledProtocols(sslEnabledProtocols: String) {
    it.property("sslEnabledProtocols", sslEnabledProtocols)
  }

  public fun sslEndpointAlgorithm(sslEndpointAlgorithm: String) {
    it.property("sslEndpointAlgorithm", sslEndpointAlgorithm)
  }

  public fun sslKeymanagerAlgorithm(sslKeymanagerAlgorithm: String) {
    it.property("sslKeymanagerAlgorithm", sslKeymanagerAlgorithm)
  }

  public fun sslKeyPassword(sslKeyPassword: String) {
    it.property("sslKeyPassword", sslKeyPassword)
  }

  public fun sslKeystoreLocation(sslKeystoreLocation: String) {
    it.property("sslKeystoreLocation", sslKeystoreLocation)
  }

  public fun sslKeystorePassword(sslKeystorePassword: String) {
    it.property("sslKeystorePassword", sslKeystorePassword)
  }

  public fun sslKeystoreType(sslKeystoreType: String) {
    it.property("sslKeystoreType", sslKeystoreType)
  }

  public fun sslProtocol(sslProtocol: String) {
    it.property("sslProtocol", sslProtocol)
  }

  public fun sslProvider(sslProvider: String) {
    it.property("sslProvider", sslProvider)
  }

  public fun sslTrustmanagerAlgorithm(sslTrustmanagerAlgorithm: String) {
    it.property("sslTrustmanagerAlgorithm", sslTrustmanagerAlgorithm)
  }

  public fun sslTruststoreLocation(sslTruststoreLocation: String) {
    it.property("sslTruststoreLocation", sslTruststoreLocation)
  }

  public fun sslTruststorePassword(sslTruststorePassword: String) {
    it.property("sslTruststorePassword", sslTruststorePassword)
  }

  public fun sslTruststoreType(sslTruststoreType: String) {
    it.property("sslTruststoreType", sslTruststoreType)
  }
}
