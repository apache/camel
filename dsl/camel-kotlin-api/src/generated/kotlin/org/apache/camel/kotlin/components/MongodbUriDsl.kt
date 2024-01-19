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

public fun UriDsl.mongodb(i: MongodbUriDsl.() -> Unit) {
  MongodbUriDsl(this).apply(i)
}

@CamelDslMarker
public class MongodbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mongodb")
  }

  private var connectionBean: String = ""

  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  public fun collection(collection: String) {
    it.property("collection", collection)
  }

  public fun collectionIndex(collectionIndex: String) {
    it.property("collectionIndex", collectionIndex)
  }

  public fun createCollection(createCollection: String) {
    it.property("createCollection", createCollection)
  }

  public fun createCollection(createCollection: Boolean) {
    it.property("createCollection", createCollection.toString())
  }

  public fun database(database: String) {
    it.property("database", database)
  }

  public fun hosts(hosts: String) {
    it.property("hosts", hosts)
  }

  public fun mongoConnection(mongoConnection: String) {
    it.property("mongoConnection", mongoConnection)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  public fun consumerType(consumerType: String) {
    it.property("consumerType", consumerType)
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

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun connectionUriString(connectionUriString: String) {
    it.property("connectionUriString", connectionUriString)
  }

  public fun appName(appName: String) {
    it.property("appName", appName)
  }

  public fun compressors(compressors: String) {
    it.property("compressors", compressors)
  }

  public fun connectTimeoutMS(connectTimeoutMS: String) {
    it.property("connectTimeoutMS", connectTimeoutMS)
  }

  public fun connectTimeoutMS(connectTimeoutMS: Int) {
    it.property("connectTimeoutMS", connectTimeoutMS.toString())
  }

  public fun cursorRegenerationDelay(cursorRegenerationDelay: String) {
    it.property("cursorRegenerationDelay", cursorRegenerationDelay)
  }

  public fun directConnection(directConnection: String) {
    it.property("directConnection", directConnection)
  }

  public fun directConnection(directConnection: Boolean) {
    it.property("directConnection", directConnection.toString())
  }

  public fun dynamicity(dynamicity: String) {
    it.property("dynamicity", dynamicity)
  }

  public fun dynamicity(dynamicity: Boolean) {
    it.property("dynamicity", dynamicity.toString())
  }

  public fun heartbeatFrequencyMS(heartbeatFrequencyMS: String) {
    it.property("heartbeatFrequencyMS", heartbeatFrequencyMS)
  }

  public fun heartbeatFrequencyMS(heartbeatFrequencyMS: Int) {
    it.property("heartbeatFrequencyMS", heartbeatFrequencyMS.toString())
  }

  public fun loadBalanced(loadBalanced: String) {
    it.property("loadBalanced", loadBalanced)
  }

  public fun loadBalanced(loadBalanced: Boolean) {
    it.property("loadBalanced", loadBalanced.toString())
  }

  public fun localThresholdMS(localThresholdMS: String) {
    it.property("localThresholdMS", localThresholdMS)
  }

  public fun localThresholdMS(localThresholdMS: Int) {
    it.property("localThresholdMS", localThresholdMS.toString())
  }

  public fun maxConnecting(maxConnecting: String) {
    it.property("maxConnecting", maxConnecting)
  }

  public fun maxConnecting(maxConnecting: Int) {
    it.property("maxConnecting", maxConnecting.toString())
  }

  public fun maxIdleTimeMS(maxIdleTimeMS: String) {
    it.property("maxIdleTimeMS", maxIdleTimeMS)
  }

  public fun maxIdleTimeMS(maxIdleTimeMS: Int) {
    it.property("maxIdleTimeMS", maxIdleTimeMS.toString())
  }

  public fun maxLifeTimeMS(maxLifeTimeMS: String) {
    it.property("maxLifeTimeMS", maxLifeTimeMS)
  }

  public fun maxLifeTimeMS(maxLifeTimeMS: Int) {
    it.property("maxLifeTimeMS", maxLifeTimeMS.toString())
  }

  public fun maxPoolSize(maxPoolSize: String) {
    it.property("maxPoolSize", maxPoolSize)
  }

  public fun maxPoolSize(maxPoolSize: Int) {
    it.property("maxPoolSize", maxPoolSize.toString())
  }

  public fun maxStalenessSeconds(maxStalenessSeconds: String) {
    it.property("maxStalenessSeconds", maxStalenessSeconds)
  }

  public fun maxStalenessSeconds(maxStalenessSeconds: Int) {
    it.property("maxStalenessSeconds", maxStalenessSeconds.toString())
  }

  public fun minPoolSize(minPoolSize: String) {
    it.property("minPoolSize", minPoolSize)
  }

  public fun minPoolSize(minPoolSize: Int) {
    it.property("minPoolSize", minPoolSize.toString())
  }

  public fun readPreference(readPreference: String) {
    it.property("readPreference", readPreference)
  }

  public fun readPreferenceTags(readPreferenceTags: String) {
    it.property("readPreferenceTags", readPreferenceTags)
  }

  public fun replicaSet(replicaSet: String) {
    it.property("replicaSet", replicaSet)
  }

  public fun retryReads(retryReads: String) {
    it.property("retryReads", retryReads)
  }

  public fun retryReads(retryReads: Boolean) {
    it.property("retryReads", retryReads.toString())
  }

  public fun retryWrites(retryWrites: String) {
    it.property("retryWrites", retryWrites)
  }

  public fun retryWrites(retryWrites: Boolean) {
    it.property("retryWrites", retryWrites.toString())
  }

  public fun serverSelectionTimeoutMS(serverSelectionTimeoutMS: String) {
    it.property("serverSelectionTimeoutMS", serverSelectionTimeoutMS)
  }

  public fun serverSelectionTimeoutMS(serverSelectionTimeoutMS: Int) {
    it.property("serverSelectionTimeoutMS", serverSelectionTimeoutMS.toString())
  }

  public fun socketTimeoutMS(socketTimeoutMS: String) {
    it.property("socketTimeoutMS", socketTimeoutMS)
  }

  public fun socketTimeoutMS(socketTimeoutMS: Int) {
    it.property("socketTimeoutMS", socketTimeoutMS.toString())
  }

  public fun srvMaxHosts(srvMaxHosts: String) {
    it.property("srvMaxHosts", srvMaxHosts)
  }

  public fun srvMaxHosts(srvMaxHosts: Int) {
    it.property("srvMaxHosts", srvMaxHosts.toString())
  }

  public fun srvServiceName(srvServiceName: String) {
    it.property("srvServiceName", srvServiceName)
  }

  public fun tls(tls: String) {
    it.property("tls", tls)
  }

  public fun tls(tls: Boolean) {
    it.property("tls", tls.toString())
  }

  public fun tlsAllowInvalidHostnames(tlsAllowInvalidHostnames: String) {
    it.property("tlsAllowInvalidHostnames", tlsAllowInvalidHostnames)
  }

  public fun tlsAllowInvalidHostnames(tlsAllowInvalidHostnames: Boolean) {
    it.property("tlsAllowInvalidHostnames", tlsAllowInvalidHostnames.toString())
  }

  public fun waitQueueTimeoutMS(waitQueueTimeoutMS: String) {
    it.property("waitQueueTimeoutMS", waitQueueTimeoutMS)
  }

  public fun waitQueueTimeoutMS(waitQueueTimeoutMS: Int) {
    it.property("waitQueueTimeoutMS", waitQueueTimeoutMS.toString())
  }

  public fun writeConcern(writeConcern: String) {
    it.property("writeConcern", writeConcern)
  }

  public fun writeResultAsHeader(writeResultAsHeader: String) {
    it.property("writeResultAsHeader", writeResultAsHeader)
  }

  public fun writeResultAsHeader(writeResultAsHeader: Boolean) {
    it.property("writeResultAsHeader", writeResultAsHeader.toString())
  }

  public fun zlibCompressionLevel(zlibCompressionLevel: String) {
    it.property("zlibCompressionLevel", zlibCompressionLevel)
  }

  public fun zlibCompressionLevel(zlibCompressionLevel: Int) {
    it.property("zlibCompressionLevel", zlibCompressionLevel.toString())
  }

  public fun fullDocument(fullDocument: String) {
    it.property("fullDocument", fullDocument)
  }

  public fun streamFilter(streamFilter: String) {
    it.property("streamFilter", streamFilter)
  }

  public fun authSource(authSource: String) {
    it.property("authSource", authSource)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun persistentId(persistentId: String) {
    it.property("persistentId", persistentId)
  }

  public fun persistentTailTracking(persistentTailTracking: String) {
    it.property("persistentTailTracking", persistentTailTracking)
  }

  public fun persistentTailTracking(persistentTailTracking: Boolean) {
    it.property("persistentTailTracking", persistentTailTracking.toString())
  }

  public fun tailTrackCollection(tailTrackCollection: String) {
    it.property("tailTrackCollection", tailTrackCollection)
  }

  public fun tailTrackDb(tailTrackDb: String) {
    it.property("tailTrackDb", tailTrackDb)
  }

  public fun tailTrackField(tailTrackField: String) {
    it.property("tailTrackField", tailTrackField)
  }

  public fun tailTrackIncreasingField(tailTrackIncreasingField: String) {
    it.property("tailTrackIncreasingField", tailTrackIncreasingField)
  }
}
