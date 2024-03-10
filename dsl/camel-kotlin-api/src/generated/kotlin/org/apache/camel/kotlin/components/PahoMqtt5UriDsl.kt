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
 * Communicate with MQTT message brokers using Eclipse Paho MQTT v5 Client.
 */
public fun UriDsl.`paho-mqtt5`(i: PahoMqtt5UriDsl.() -> Unit) {
  PahoMqtt5UriDsl(this).apply(i)
}

@CamelDslMarker
public class PahoMqtt5UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("paho-mqtt5")
  }

  private var topic: String = ""

  /**
   * Name of the topic
   */
  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  /**
   * Sets whether the client will automatically attempt to reconnect to the server if the connection
   * is lost. If set to false, the client will not attempt to automatically reconnect to the server in
   * the event that the connection is lost. If set to true, in the event that the connection is lost,
   * the client will attempt to reconnect to the server. It will initially wait 1 second before it
   * attempts to reconnect, for every failed reconnect attempt, the delay will double until it is at 2
   * minutes at which point the delay will stay at 2 minutes.
   */
  public fun automaticReconnect(automaticReconnect: String) {
    it.property("automaticReconnect", automaticReconnect)
  }

  /**
   * Sets whether the client will automatically attempt to reconnect to the server if the connection
   * is lost. If set to false, the client will not attempt to automatically reconnect to the server in
   * the event that the connection is lost. If set to true, in the event that the connection is lost,
   * the client will attempt to reconnect to the server. It will initially wait 1 second before it
   * attempts to reconnect, for every failed reconnect attempt, the delay will double until it is at 2
   * minutes at which point the delay will stay at 2 minutes.
   */
  public fun automaticReconnect(automaticReconnect: Boolean) {
    it.property("automaticReconnect", automaticReconnect.toString())
  }

  /**
   * The URL of the MQTT broker.
   */
  public fun brokerUrl(brokerUrl: String) {
    it.property("brokerUrl", brokerUrl)
  }

  /**
   * Sets whether the client and server should remember state across restarts and reconnects. If set
   * to false both the client and server will maintain state across restarts of the client, the server
   * and the connection. As state is maintained: Message delivery will be reliable meeting the
   * specified QOS even if the client, server or connection are restarted. The server will treat a
   * subscription as durable. If set to true the client and server will not maintain state across
   * restarts of the client, the server or the connection. This means Message delivery to the specified
   * QOS cannot be maintained if the client, server or connection are restarted The server will treat a
   * subscription as non-durable
   */
  public fun cleanStart(cleanStart: String) {
    it.property("cleanStart", cleanStart)
  }

  /**
   * Sets whether the client and server should remember state across restarts and reconnects. If set
   * to false both the client and server will maintain state across restarts of the client, the server
   * and the connection. As state is maintained: Message delivery will be reliable meeting the
   * specified QOS even if the client, server or connection are restarted. The server will treat a
   * subscription as durable. If set to true the client and server will not maintain state across
   * restarts of the client, the server or the connection. This means Message delivery to the specified
   * QOS cannot be maintained if the client, server or connection are restarted The server will treat a
   * subscription as non-durable
   */
  public fun cleanStart(cleanStart: Boolean) {
    it.property("cleanStart", cleanStart.toString())
  }

  /**
   * MQTT client identifier. The identifier must be unique.
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * Sets the connection timeout value. This value, measured in seconds, defines the maximum time
   * interval the client will wait for the network connection to the MQTT server to be established. The
   * default timeout is 30 seconds. A value of 0 disables timeout processing meaning the client will
   * wait until the network connection is made successfully or fails.
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Sets the connection timeout value. This value, measured in seconds, defines the maximum time
   * interval the client will wait for the network connection to the MQTT server to be established. The
   * default timeout is 30 seconds. A value of 0 disables timeout processing meaning the client will
   * wait until the network connection is made successfully or fails.
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * Base directory used by file persistence. Will by default use user directory.
   */
  public fun filePersistenceDirectory(filePersistenceDirectory: String) {
    it.property("filePersistenceDirectory", filePersistenceDirectory)
  }

  /**
   * Sets the keep alive interval. This value, measured in seconds, defines the maximum time
   * interval between messages sent or received. It enables the client to detect if the server is no
   * longer available, without having to wait for the TCP/IP timeout. The client will ensure that at
   * least one message travels across the network within each keep alive period. In the absence of a
   * data-related message during the time period, the client sends a very small ping message, which the
   * server will acknowledge. A value of 0 disables keepalive processing in the client. The default
   * value is 60 seconds
   */
  public fun keepAliveInterval(keepAliveInterval: String) {
    it.property("keepAliveInterval", keepAliveInterval)
  }

  /**
   * Sets the keep alive interval. This value, measured in seconds, defines the maximum time
   * interval between messages sent or received. It enables the client to detect if the server is no
   * longer available, without having to wait for the TCP/IP timeout. The client will ensure that at
   * least one message travels across the network within each keep alive period. In the absence of a
   * data-related message during the time period, the client sends a very small ping message, which the
   * server will acknowledge. A value of 0 disables keepalive processing in the client. The default
   * value is 60 seconds
   */
  public fun keepAliveInterval(keepAliveInterval: Int) {
    it.property("keepAliveInterval", keepAliveInterval.toString())
  }

  /**
   * Get the maximum time (in millis) to wait between reconnects
   */
  public fun maxReconnectDelay(maxReconnectDelay: String) {
    it.property("maxReconnectDelay", maxReconnectDelay)
  }

  /**
   * Get the maximum time (in millis) to wait between reconnects
   */
  public fun maxReconnectDelay(maxReconnectDelay: Int) {
    it.property("maxReconnectDelay", maxReconnectDelay.toString())
  }

  /**
   * Client persistence to be used - memory or file.
   */
  public fun persistence(persistence: String) {
    it.property("persistence", persistence)
  }

  /**
   * Client quality of service level (0-2).
   */
  public fun qos(qos: String) {
    it.property("qos", qos)
  }

  /**
   * Client quality of service level (0-2).
   */
  public fun qos(qos: Int) {
    it.property("qos", qos.toString())
  }

  /**
   * Sets the Receive Maximum. This value represents the limit of QoS 1 and QoS 2 publications that
   * the client is willing to process concurrently. There is no mechanism to limit the number of QoS 0
   * publications that the Server might try to send. The default value is 65535
   */
  public fun receiveMaximum(receiveMaximum: String) {
    it.property("receiveMaximum", receiveMaximum)
  }

  /**
   * Sets the Receive Maximum. This value represents the limit of QoS 1 and QoS 2 publications that
   * the client is willing to process concurrently. There is no mechanism to limit the number of QoS 0
   * publications that the Server might try to send. The default value is 65535
   */
  public fun receiveMaximum(receiveMaximum: Int) {
    it.property("receiveMaximum", receiveMaximum.toString())
  }

  /**
   * Retain option
   */
  public fun retained(retained: String) {
    it.property("retained", retained)
  }

  /**
   * Retain option
   */
  public fun retained(retained: Boolean) {
    it.property("retained", retained.toString())
  }

  /**
   * Set a list of one or more serverURIs the client may connect to. Multiple servers can be
   * separated by comma. Each serverURI specifies the address of a server that the client may connect
   * to. Two types of connection are supported tcp:// for a TCP connection and ssl:// for a TCP
   * connection secured by SSL/TLS. For example: tcp://localhost:1883 ssl://localhost:8883 If the port
   * is not specified, it will default to 1883 for tcp:// URIs, and 8883 for ssl:// URIs. If serverURIs
   * is set then it overrides the serverURI parameter passed in on the constructor of the MQTT client.
   * When an attempt to connect is initiated the client will start with the first serverURI in the list
   * and work through the list until a connection is established with a server. If a connection cannot
   * be made to any of the servers then the connect attempt fails. Specifying a list of servers that a
   * client may connect to has several uses: High Availability and reliable message delivery Some MQTT
   * servers support a high availability feature where two or more equal MQTT servers share state. An
   * MQTT client can connect to any of the equal servers and be assured that messages are reliably
   * delivered and durable subscriptions are maintained no matter which server the client connects to.
   * The cleansession flag must be set to false if durable subscriptions and/or reliable message
   * delivery is required. Hunt List A set of servers may be specified that are not equal (as in the
   * high availability option). As no state is shared across the servers reliable message delivery and
   * durable subscriptions are not valid. The cleansession flag must be set to true if the hunt list
   * mode is used
   */
  public fun serverURIs(serverURIs: String) {
    it.property("serverURIs", serverURIs)
  }

  /**
   * Sets the Session Expiry Interval. This value, measured in seconds, defines the maximum time
   * that the broker will maintain the session for once the client disconnects. Clients should only
   * connect with a long Session Expiry interval if they intend to connect to the server at some later
   * point in time. By default this value is -1 and so will not be sent, in this case, the session will
   * not expire. If a 0 is sent, the session will end immediately once the Network Connection is
   * closed. When the client has determined that it has no longer any use for the session, it should
   * disconnect with a Session Expiry Interval set to 0.
   */
  public fun sessionExpiryInterval(sessionExpiryInterval: String) {
    it.property("sessionExpiryInterval", sessionExpiryInterval)
  }

  /**
   * Sets the Session Expiry Interval. This value, measured in seconds, defines the maximum time
   * that the broker will maintain the session for once the client disconnects. Clients should only
   * connect with a long Session Expiry interval if they intend to connect to the server at some later
   * point in time. By default this value is -1 and so will not be sent, in this case, the session will
   * not expire. If a 0 is sent, the session will end immediately once the Network Connection is
   * closed. When the client has determined that it has no longer any use for the session, it should
   * disconnect with a Session Expiry Interval set to 0.
   */
  public fun sessionExpiryInterval(sessionExpiryInterval: Int) {
    it.property("sessionExpiryInterval", sessionExpiryInterval.toString())
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. The MQTT properties set for the message.
   */
  public fun willMqttProperties(willMqttProperties: String) {
    it.property("willMqttProperties", willMqttProperties)
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. The byte payload for the message.
   */
  public fun willPayload(willPayload: String) {
    it.property("willPayload", willPayload)
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. The quality of service to publish the message at (0, 1 or 2).
   */
  public fun willQos(willQos: String) {
    it.property("willQos", willQos)
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. The quality of service to publish the message at (0, 1 or 2).
   */
  public fun willQos(willQos: Int) {
    it.property("willQos", willQos.toString())
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. Whether or not the message should be retained.
   */
  public fun willRetained(willRetained: String) {
    it.property("willRetained", willRetained)
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. Whether or not the message should be retained.
   */
  public fun willRetained(willRetained: Boolean) {
    it.property("willRetained", willRetained.toString())
  }

  /**
   * Sets the Last Will and Testament (LWT) for the connection. In the event that this client
   * unexpectedly loses its connection to the server, the server will publish a message to itself using
   * the supplied details. The topic to publish to.
   */
  public fun willTopic(willTopic: String) {
    it.property("willTopic", willTopic)
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
   * To use an existing mqtt client
   */
  public fun client(client: String) {
    it.property("client", client)
  }

  /**
   * Sets the Custom WebSocket Headers for the WebSocket Connection.
   */
  public fun customWebSocketHeaders(customWebSocketHeaders: String) {
    it.property("customWebSocketHeaders", customWebSocketHeaders)
  }

  /**
   * Set the time in seconds that the executor service should wait when terminating before
   * forcefully terminating. It is not recommended to change this value unless you are absolutely sure
   * that you need to.
   */
  public fun executorServiceTimeout(executorServiceTimeout: String) {
    it.property("executorServiceTimeout", executorServiceTimeout)
  }

  /**
   * Set the time in seconds that the executor service should wait when terminating before
   * forcefully terminating. It is not recommended to change this value unless you are absolutely sure
   * that you need to.
   */
  public fun executorServiceTimeout(executorServiceTimeout: Int) {
    it.property("executorServiceTimeout", executorServiceTimeout.toString())
  }

  /**
   * Whether SSL HostnameVerifier is enabled or not. The default value is true.
   */
  public fun httpsHostnameVerificationEnabled(httpsHostnameVerificationEnabled: String) {
    it.property("httpsHostnameVerificationEnabled", httpsHostnameVerificationEnabled)
  }

  /**
   * Whether SSL HostnameVerifier is enabled or not. The default value is true.
   */
  public fun httpsHostnameVerificationEnabled(httpsHostnameVerificationEnabled: Boolean) {
    it.property("httpsHostnameVerificationEnabled", httpsHostnameVerificationEnabled.toString())
  }

  /**
   * Password to be used for authentication against the MQTT broker
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Sets the SocketFactory to use. This allows an application to apply its own policies around the
   * creation of network sockets. If using an SSL connection, an SSLSocketFactory can be used to supply
   * application-specific security settings.
   */
  public fun socketFactory(socketFactory: String) {
    it.property("socketFactory", socketFactory)
  }

  /**
   * Sets the SSL properties for the connection. Note that these properties are only valid if an
   * implementation of the Java Secure Socket Extensions (JSSE) is available. These properties are not
   * used if a custom SocketFactory has been set. The following properties can be used:
   * com.ibm.ssl.protocol One of: SSL, SSLv3, TLS, TLSv1, SSL_TLS. com.ibm.ssl.contextProvider
   * Underlying JSSE provider. For example IBMJSSE2 or SunJSSE com.ibm.ssl.keyStore The name of the
   * file that contains the KeyStore object that you want the KeyManager to use. For example
   * /mydir/etc/key.p12 com.ibm.ssl.keyStorePassword The password for the KeyStore object that you want
   * the KeyManager to use. The password can either be in plain-text, or may be obfuscated using the
   * static method: com.ibm.micro.security.Password.obfuscate(char password). This obfuscates the
   * password using a simple and insecure XOR and Base64 encoding mechanism. Note that this is only a
   * simple scrambler to obfuscate clear-text passwords. com.ibm.ssl.keyStoreType Type of key store,
   * for example PKCS12, JKS, or JCEKS. com.ibm.ssl.keyStoreProvider Key store provider, for example
   * IBMJCE or IBMJCEFIPS. com.ibm.ssl.trustStore The name of the file that contains the KeyStore
   * object that you want the TrustManager to use. com.ibm.ssl.trustStorePassword The password for the
   * TrustStore object that you want the TrustManager to use. The password can either be in plain-text,
   * or may be obfuscated using the static method: com.ibm.micro.security.Password.obfuscate(char
   * password). This obfuscates the password using a simple and insecure XOR and Base64 encoding
   * mechanism. Note that this is only a simple scrambler to obfuscate clear-text passwords.
   * com.ibm.ssl.trustStoreType The type of KeyStore object that you want the default TrustManager to
   * use. Same possible values as keyStoreType. com.ibm.ssl.trustStoreProvider Trust store provider,
   * for example IBMJCE or IBMJCEFIPS. com.ibm.ssl.enabledCipherSuites A list of which ciphers are
   * enabled. Values are dependent on the provider, for example:
   * SSL_RSA_WITH_AES_128_CBC_SHA;SSL_RSA_WITH_3DES_EDE_CBC_SHA. com.ibm.ssl.keyManager Sets the
   * algorithm that will be used to instantiate a KeyManagerFactory object instead of using the default
   * algorithm available in the platform. Example values: IbmX509 or IBMJ9X509.
   * com.ibm.ssl.trustManager Sets the algorithm that will be used to instantiate a TrustManagerFactory
   * object instead of using the default algorithm available in the platform. Example values: PKIX or
   * IBMJ9X509.
   */
  public fun sslClientProps(sslClientProps: String) {
    it.property("sslClientProps", sslClientProps)
  }

  /**
   * Sets the HostnameVerifier for the SSL connection. Note that it will be used after handshake on
   * a connection and you should do actions by yourself when hostname is verified error. There is no
   * default HostnameVerifier
   */
  public fun sslHostnameVerifier(sslHostnameVerifier: String) {
    it.property("sslHostnameVerifier", sslHostnameVerifier)
  }

  /**
   * Username to be used for authentication against the MQTT broker
   */
  public fun userName(userName: String) {
    it.property("userName", userName)
  }
}
