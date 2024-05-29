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
 * Perform operations on MongoDB documents and collections.
 */
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

  /**
   * Sets the connection bean reference used to lookup a client for connecting to a database if no
   * hosts parameter is present.
   */
  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  /**
   * Sets the name of the MongoDB collection to bind to this endpoint
   */
  public fun collection(collection: String) {
    it.property("collection", collection)
  }

  /**
   * Sets the collection index (JSON FORMAT : { field1 : order1, field2 : order2})
   */
  public fun collectionIndex(collectionIndex: String) {
    it.property("collectionIndex", collectionIndex)
  }

  /**
   * Set the whole Connection String/Uri for mongodb endpoint.
   */
  public fun connectionUriString(connectionUriString: String) {
    it.property("connectionUriString", connectionUriString)
  }

  /**
   * Create collection during initialisation if it doesn't exist. Default is true.
   */
  public fun createCollection(createCollection: String) {
    it.property("createCollection", createCollection)
  }

  /**
   * Create collection during initialisation if it doesn't exist. Default is true.
   */
  public fun createCollection(createCollection: Boolean) {
    it.property("createCollection", createCollection.toString())
  }

  /**
   * Sets the name of the MongoDB database to target
   */
  public fun database(database: String) {
    it.property("database", database)
  }

  /**
   * Host address of mongodb server in host:port format. It's possible also use more than one
   * address, as comma separated list of hosts: host1:port1,host2:port2. If the hosts parameter is
   * specified, the provided connectionBean is ignored.
   */
  public fun hosts(hosts: String) {
    it.property("hosts", hosts)
  }

  /**
   * Sets the connection bean used as a client for connecting to a database.
   */
  public fun mongoConnection(mongoConnection: String) {
    it.property("mongoConnection", mongoConnection)
  }

  /**
   * Sets the operation this endpoint will execute against MongoDB.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Convert the output of the producer to the selected type : DocumentList Document or
   * MongoIterable. DocumentList or MongoIterable applies to findAll and aggregate. Document applies to
   * all other operations.
   */
  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  /**
   * Consumer type.
   */
  public fun consumerType(consumerType: String) {
    it.property("consumerType", consumerType)
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
   * Sets the logical name of the application. The application name may be used by the client to
   * identify the application to the server, for use in server logs, slow query logs, and profile
   * collection. Default: null
   */
  public fun appName(appName: String) {
    it.property("appName", appName)
  }

  /**
   * Specifies one or more compression algorithms that the driver will attempt to use to compress
   * requests sent to the connected MongoDB instance. Possible values include: zlib, snappy, and zstd.
   * Default: null
   */
  public fun compressors(compressors: String) {
    it.property("compressors", compressors)
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver waits for a connection
   * to open before timing out. A value of 0 instructs the driver to never time out while waiting for a
   * connection to open. Default: 10000 (10 seconds)
   */
  public fun connectTimeoutMS(connectTimeoutMS: String) {
    it.property("connectTimeoutMS", connectTimeoutMS)
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver waits for a connection
   * to open before timing out. A value of 0 instructs the driver to never time out while waiting for a
   * connection to open. Default: 10000 (10 seconds)
   */
  public fun connectTimeoutMS(connectTimeoutMS: Int) {
    it.property("connectTimeoutMS", connectTimeoutMS.toString())
  }

  /**
   * MongoDB tailable cursors will block until new data arrives. If no new data is inserted, after
   * some time the cursor will be automatically freed and closed by the MongoDB server. The client is
   * expected to regenerate the cursor if needed. This value specifies the time to wait before
   * attempting to fetch a new cursor, and if the attempt fails, how long before the next attempt is
   * made. Default value is 1000ms.
   */
  public fun cursorRegenerationDelay(cursorRegenerationDelay: String) {
    it.property("cursorRegenerationDelay", cursorRegenerationDelay)
  }

  /**
   * Specifies that the driver must connect to the host directly. Default: false
   */
  public fun directConnection(directConnection: String) {
    it.property("directConnection", directConnection)
  }

  /**
   * Specifies that the driver must connect to the host directly. Default: false
   */
  public fun directConnection(directConnection: Boolean) {
    it.property("directConnection", directConnection.toString())
  }

  /**
   * Sets whether this endpoint will attempt to dynamically resolve the target database and
   * collection from the incoming Exchange properties. Can be used to override at runtime the database
   * and collection specified on the otherwise static endpoint URI. It is disabled by default to boost
   * performance. Enabling it will take a minimal performance hit.
   */
  public fun dynamicity(dynamicity: String) {
    it.property("dynamicity", dynamicity)
  }

  /**
   * Sets whether this endpoint will attempt to dynamically resolve the target database and
   * collection from the incoming Exchange properties. Can be used to override at runtime the database
   * and collection specified on the otherwise static endpoint URI. It is disabled by default to boost
   * performance. Enabling it will take a minimal performance hit.
   */
  public fun dynamicity(dynamicity: Boolean) {
    it.property("dynamicity", dynamicity.toString())
  }

  /**
   * heartbeatFrequencyMS controls when the driver checks the state of the MongoDB deployment.
   * Specify the interval (in milliseconds) between checks, counted from the end of the previous check
   * until the beginning of the next one. Default: Single-threaded drivers: 60 seconds. Multi-threaded
   * drivers: 10 seconds.
   */
  public fun heartbeatFrequencyMS(heartbeatFrequencyMS: String) {
    it.property("heartbeatFrequencyMS", heartbeatFrequencyMS)
  }

  /**
   * heartbeatFrequencyMS controls when the driver checks the state of the MongoDB deployment.
   * Specify the interval (in milliseconds) between checks, counted from the end of the previous check
   * until the beginning of the next one. Default: Single-threaded drivers: 60 seconds. Multi-threaded
   * drivers: 10 seconds.
   */
  public fun heartbeatFrequencyMS(heartbeatFrequencyMS: Int) {
    it.property("heartbeatFrequencyMS", heartbeatFrequencyMS.toString())
  }

  /**
   * If true the driver will assume that it's connecting to MongoDB through a load balancer.
   */
  public fun loadBalanced(loadBalanced: String) {
    it.property("loadBalanced", loadBalanced)
  }

  /**
   * If true the driver will assume that it's connecting to MongoDB through a load balancer.
   */
  public fun loadBalanced(loadBalanced: Boolean) {
    it.property("loadBalanced", loadBalanced.toString())
  }

  /**
   * The size (in milliseconds) of the latency window for selecting among multiple suitable MongoDB
   * instances. Default: 15 milliseconds.
   */
  public fun localThresholdMS(localThresholdMS: String) {
    it.property("localThresholdMS", localThresholdMS)
  }

  /**
   * The size (in milliseconds) of the latency window for selecting among multiple suitable MongoDB
   * instances. Default: 15 milliseconds.
   */
  public fun localThresholdMS(localThresholdMS: Int) {
    it.property("localThresholdMS", localThresholdMS.toString())
  }

  /**
   * Specifies the maximum number of connections a pool may be establishing concurrently. Default: 2
   */
  public fun maxConnecting(maxConnecting: String) {
    it.property("maxConnecting", maxConnecting)
  }

  /**
   * Specifies the maximum number of connections a pool may be establishing concurrently. Default: 2
   */
  public fun maxConnecting(maxConnecting: Int) {
    it.property("maxConnecting", maxConnecting.toString())
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver will allow a pooled
   * connection to idle before closing the connection. A value of 0 indicates that there is no upper
   * bound on how long the driver can allow a pooled collection to be idle. Default: 0
   */
  public fun maxIdleTimeMS(maxIdleTimeMS: String) {
    it.property("maxIdleTimeMS", maxIdleTimeMS)
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver will allow a pooled
   * connection to idle before closing the connection. A value of 0 indicates that there is no upper
   * bound on how long the driver can allow a pooled collection to be idle. Default: 0
   */
  public fun maxIdleTimeMS(maxIdleTimeMS: Int) {
    it.property("maxIdleTimeMS", maxIdleTimeMS.toString())
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver will continue to use a
   * pooled connection before closing the connection. A value of 0 indicates that there is no upper
   * bound on how long the driver can keep a pooled connection open. Default: 0
   */
  public fun maxLifeTimeMS(maxLifeTimeMS: String) {
    it.property("maxLifeTimeMS", maxLifeTimeMS)
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver will continue to use a
   * pooled connection before closing the connection. A value of 0 indicates that there is no upper
   * bound on how long the driver can keep a pooled connection open. Default: 0
   */
  public fun maxLifeTimeMS(maxLifeTimeMS: Int) {
    it.property("maxLifeTimeMS", maxLifeTimeMS.toString())
  }

  /**
   * The maximum number of connections in the connection pool. The default value is 100.
   */
  public fun maxPoolSize(maxPoolSize: String) {
    it.property("maxPoolSize", maxPoolSize)
  }

  /**
   * The maximum number of connections in the connection pool. The default value is 100.
   */
  public fun maxPoolSize(maxPoolSize: Int) {
    it.property("maxPoolSize", maxPoolSize.toString())
  }

  /**
   * Specifies, in seconds, how stale a secondary can be before the driver stops communicating with
   * that secondary. The minimum value is either 90 seconds or the heartbeat frequency plus 10 seconds,
   * whichever is greater. For more information, see the server documentation for the
   * maxStalenessSeconds option. Not providing a parameter or explicitly specifying -1 indicates that
   * there should be no staleness check for secondaries. Default: -1
   */
  public fun maxStalenessSeconds(maxStalenessSeconds: String) {
    it.property("maxStalenessSeconds", maxStalenessSeconds)
  }

  /**
   * Specifies, in seconds, how stale a secondary can be before the driver stops communicating with
   * that secondary. The minimum value is either 90 seconds or the heartbeat frequency plus 10 seconds,
   * whichever is greater. For more information, see the server documentation for the
   * maxStalenessSeconds option. Not providing a parameter or explicitly specifying -1 indicates that
   * there should be no staleness check for secondaries. Default: -1
   */
  public fun maxStalenessSeconds(maxStalenessSeconds: Int) {
    it.property("maxStalenessSeconds", maxStalenessSeconds.toString())
  }

  /**
   * Specifies the minimum number of connections that must exist at any moment in a single
   * connection pool. Default: 0
   */
  public fun minPoolSize(minPoolSize: String) {
    it.property("minPoolSize", minPoolSize)
  }

  /**
   * Specifies the minimum number of connections that must exist at any moment in a single
   * connection pool. Default: 0
   */
  public fun minPoolSize(minPoolSize: Int) {
    it.property("minPoolSize", minPoolSize.toString())
  }

  /**
   * Configure how MongoDB clients route read operations to the members of a replica set. Possible
   * values are PRIMARY, PRIMARY_PREFERRED, SECONDARY, SECONDARY_PREFERRED or NEAREST
   */
  public fun readPreference(readPreference: String) {
    it.property("readPreference", readPreference)
  }

  /**
   * A representation of a tag set as a comma-separated list of colon-separated key-value pairs,
   * e.g. dc:ny,rack:1. Spaces are stripped from beginning and end of all keys and values. To specify a
   * list of tag sets, using multiple readPreferenceTags, e.g.
   * readPreferenceTags=dc:ny,rack:1;readPreferenceTags=dc:ny;readPreferenceTags= Note the empty value
   * for the last one, which means match any secondary as a last resort. Order matters when using
   * multiple readPreferenceTags.
   */
  public fun readPreferenceTags(readPreferenceTags: String) {
    it.property("readPreferenceTags", readPreferenceTags)
  }

  /**
   * Specifies that the connection string provided includes multiple hosts. When specified, the
   * driver attempts to find all members of that set.
   */
  public fun replicaSet(replicaSet: String) {
    it.property("replicaSet", replicaSet)
  }

  /**
   * Specifies that the driver must retry supported read operations if they fail due to a network
   * error. Default: true
   */
  public fun retryReads(retryReads: String) {
    it.property("retryReads", retryReads)
  }

  /**
   * Specifies that the driver must retry supported read operations if they fail due to a network
   * error. Default: true
   */
  public fun retryReads(retryReads: Boolean) {
    it.property("retryReads", retryReads.toString())
  }

  /**
   * Specifies that the driver must retry supported write operations if they fail due to a network
   * error. Default: true
   */
  public fun retryWrites(retryWrites: String) {
    it.property("retryWrites", retryWrites)
  }

  /**
   * Specifies that the driver must retry supported write operations if they fail due to a network
   * error. Default: true
   */
  public fun retryWrites(retryWrites: Boolean) {
    it.property("retryWrites", retryWrites.toString())
  }

  /**
   * Specifies how long (in milliseconds) to block for server selection before throwing an
   * exception. Default: 30,000 milliseconds.
   */
  public fun serverSelectionTimeoutMS(serverSelectionTimeoutMS: String) {
    it.property("serverSelectionTimeoutMS", serverSelectionTimeoutMS)
  }

  /**
   * Specifies how long (in milliseconds) to block for server selection before throwing an
   * exception. Default: 30,000 milliseconds.
   */
  public fun serverSelectionTimeoutMS(serverSelectionTimeoutMS: Int) {
    it.property("serverSelectionTimeoutMS", serverSelectionTimeoutMS.toString())
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver will wait to send or
   * receive a request before timing out. A value of 0 instructs the driver to never time out while
   * waiting to send or receive a request. Default: 0
   */
  public fun socketTimeoutMS(socketTimeoutMS: String) {
    it.property("socketTimeoutMS", socketTimeoutMS)
  }

  /**
   * Specifies the maximum amount of time, in milliseconds, the Java driver will wait to send or
   * receive a request before timing out. A value of 0 instructs the driver to never time out while
   * waiting to send or receive a request. Default: 0
   */
  public fun socketTimeoutMS(socketTimeoutMS: Int) {
    it.property("socketTimeoutMS", socketTimeoutMS.toString())
  }

  /**
   * The maximum number of hosts from the SRV record to connect to.
   */
  public fun srvMaxHosts(srvMaxHosts: String) {
    it.property("srvMaxHosts", srvMaxHosts)
  }

  /**
   * The maximum number of hosts from the SRV record to connect to.
   */
  public fun srvMaxHosts(srvMaxHosts: Int) {
    it.property("srvMaxHosts", srvMaxHosts.toString())
  }

  /**
   * Specifies the service name of the SRV resource recordsthe driver retrieves to construct your
   * seed list. You must use the DNS Seed List Connection Format in your connection URI to use this
   * option. Default: mongodb
   */
  public fun srvServiceName(srvServiceName: String) {
    it.property("srvServiceName", srvServiceName)
  }

  /**
   * Specifies that all communication with MongoDB instances should use TLS. Supersedes the ssl
   * option. Default: false
   */
  public fun tls(tls: String) {
    it.property("tls", tls)
  }

  /**
   * Specifies that all communication with MongoDB instances should use TLS. Supersedes the ssl
   * option. Default: false
   */
  public fun tls(tls: Boolean) {
    it.property("tls", tls.toString())
  }

  /**
   * Specifies that the driver should allow invalid hostnames in the certificate for TLS
   * connections. Supersedes sslInvalidHostNameAllowed. Has the same effect as tlsInsecure by setting
   * tlsAllowInvalidHostnames to true. Default: false
   */
  public fun tlsAllowInvalidHostnames(tlsAllowInvalidHostnames: String) {
    it.property("tlsAllowInvalidHostnames", tlsAllowInvalidHostnames)
  }

  /**
   * Specifies that the driver should allow invalid hostnames in the certificate for TLS
   * connections. Supersedes sslInvalidHostNameAllowed. Has the same effect as tlsInsecure by setting
   * tlsAllowInvalidHostnames to true. Default: false
   */
  public fun tlsAllowInvalidHostnames(tlsAllowInvalidHostnames: Boolean) {
    it.property("tlsAllowInvalidHostnames", tlsAllowInvalidHostnames.toString())
  }

  /**
   * Specifies the maximum amount of time, in milliseconds that a thread may wait for a connection
   * to become available. Default: 120000 (120 seconds)
   */
  public fun waitQueueTimeoutMS(waitQueueTimeoutMS: String) {
    it.property("waitQueueTimeoutMS", waitQueueTimeoutMS)
  }

  /**
   * Specifies the maximum amount of time, in milliseconds that a thread may wait for a connection
   * to become available. Default: 120000 (120 seconds)
   */
  public fun waitQueueTimeoutMS(waitQueueTimeoutMS: Int) {
    it.property("waitQueueTimeoutMS", waitQueueTimeoutMS.toString())
  }

  /**
   * Configure the connection bean with the level of acknowledgment requested from MongoDB for write
   * operations to a standalone mongod, replicaset or cluster. Possible values are ACKNOWLEDGED, W1,
   * W2, W3, UNACKNOWLEDGED, JOURNALED or MAJORITY.
   */
  public fun writeConcern(writeConcern: String) {
    it.property("writeConcern", writeConcern)
  }

  /**
   * In write operations, it determines whether instead of returning WriteResult as the body of the
   * OUT message, we transfer the IN message to the OUT and attach the WriteResult as a header.
   */
  public fun writeResultAsHeader(writeResultAsHeader: String) {
    it.property("writeResultAsHeader", writeResultAsHeader)
  }

  /**
   * In write operations, it determines whether instead of returning WriteResult as the body of the
   * OUT message, we transfer the IN message to the OUT and attach the WriteResult as a header.
   */
  public fun writeResultAsHeader(writeResultAsHeader: Boolean) {
    it.property("writeResultAsHeader", writeResultAsHeader.toString())
  }

  /**
   * Specifies the degree of compression that Zlib should use to decrease the size of requests to
   * the connected MongoDB instance. The level can range from -1 to 9, with lower values compressing
   * faster (but resulting in larger requests) and larger values compressing slower (but resulting in
   * smaller requests). Default: null
   */
  public fun zlibCompressionLevel(zlibCompressionLevel: String) {
    it.property("zlibCompressionLevel", zlibCompressionLevel)
  }

  /**
   * Specifies the degree of compression that Zlib should use to decrease the size of requests to
   * the connected MongoDB instance. The level can range from -1 to 9, with lower values compressing
   * faster (but resulting in larger requests) and larger values compressing slower (but resulting in
   * smaller requests). Default: null
   */
  public fun zlibCompressionLevel(zlibCompressionLevel: Int) {
    it.property("zlibCompressionLevel", zlibCompressionLevel.toString())
  }

  /**
   * Specifies whether changeStream consumer include a copy of the full document when modified by
   * update operations. Possible values are default, updateLookup, required and whenAvailable.
   */
  public fun fullDocument(fullDocument: String) {
    it.property("fullDocument", fullDocument)
  }

  /**
   * Filter condition for change streams consumer.
   */
  public fun streamFilter(streamFilter: String) {
    it.property("streamFilter", streamFilter)
  }

  /**
   * The database name associated with the user's credentials.
   */
  public fun authSource(authSource: String) {
    it.property("authSource", authSource)
  }

  /**
   * User password for mongodb connection
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Username for mongodb connection
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * One tail tracking collection can host many trackers for several tailable consumers. To keep
   * them separate, each tracker should have its own unique persistentId.
   */
  public fun persistentId(persistentId: String) {
    it.property("persistentId", persistentId)
  }

  /**
   * Enable persistent tail tracking, which is a mechanism to keep track of the last consumed
   * message across system restarts. The next time the system is up, the endpoint will recover the
   * cursor from the point where it last stopped slurping records.
   */
  public fun persistentTailTracking(persistentTailTracking: String) {
    it.property("persistentTailTracking", persistentTailTracking)
  }

  /**
   * Enable persistent tail tracking, which is a mechanism to keep track of the last consumed
   * message across system restarts. The next time the system is up, the endpoint will recover the
   * cursor from the point where it last stopped slurping records.
   */
  public fun persistentTailTracking(persistentTailTracking: Boolean) {
    it.property("persistentTailTracking", persistentTailTracking.toString())
  }

  /**
   * Collection where tail tracking information will be persisted. If not specified,
   * MongoDbTailTrackingConfig#DEFAULT_COLLECTION will be used by default.
   */
  public fun tailTrackCollection(tailTrackCollection: String) {
    it.property("tailTrackCollection", tailTrackCollection)
  }

  /**
   * Indicates what database the tail tracking mechanism will persist to. If not specified, the
   * current database will be picked by default. Dynamicity will not be taken into account even if
   * enabled, i.e. the tail tracking database will not vary past endpoint initialisation.
   */
  public fun tailTrackDb(tailTrackDb: String) {
    it.property("tailTrackDb", tailTrackDb)
  }

  /**
   * Field where the last tracked value will be placed. If not specified,
   * MongoDbTailTrackingConfig#DEFAULT_FIELD will be used by default.
   */
  public fun tailTrackField(tailTrackField: String) {
    it.property("tailTrackField", tailTrackField)
  }

  /**
   * Correlation field in the incoming record which is of increasing nature and will be used to
   * position the tailing cursor every time it is generated. The cursor will be (re)created with a
   * query of type: tailTrackIncreasingField greater than lastValue (possibly recovered from persistent
   * tail tracking). Can be of type Integer, Date, String, etc. NOTE: No support for dot notation at
   * the current time, so the field should be at the top level of the document.
   */
  public fun tailTrackIncreasingField(tailTrackIncreasingField: String) {
    it.property("tailTrackIncreasingField", tailTrackIncreasingField)
  }
}
