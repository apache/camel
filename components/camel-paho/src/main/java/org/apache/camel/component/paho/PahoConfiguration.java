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
package org.apache.camel.component.paho;

import java.util.Properties;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class PahoConfiguration implements Cloneable {

    @UriParam
    private String clientId;
    @UriParam(defaultValue = PahoConstants.DEFAULT_BROKER_URL)
    private String brokerUrl = PahoConstants.DEFAULT_BROKER_URL;
    @UriParam(defaultValue = "2")
    private int qos = PahoConstants.DEFAULT_QOS;
    @UriParam
    private boolean retained;
    @UriParam(defaultValue = "MEMORY")
    private PahoPersistence persistence = PahoPersistence.MEMORY;
    @UriParam
    private String filePersistenceDirectory;

    @UriParam(defaultValue = "60")
    private int keepAliveInterval = 60;
    @UriParam(defaultValue = "10")
    private int maxInflight = 10;
    @UriParam
    private String willTopic;
    @UriParam
    private String willPayload;
    @UriParam
    private int willQos;
    @UriParam
    private boolean willRetained;
    @UriParam(label = "security") @Metadata(secret = true)
    private String userName;
    @UriParam(label = "security") @Metadata(secret = true)
    private String password;
    @UriParam(label = "security")
    private SocketFactory socketFactory;
    @UriParam(label = "security")
    private Properties sslClientProps;
    @UriParam(label = "security", defaultValue = "true")
    private boolean httpsHostnameVerificationEnabled = true;
    @UriParam(label = "security")
    private HostnameVerifier sslHostnameVerifier;
    @UriParam(defaultValue = "true")
    private boolean cleanSession = true;
    @UriParam(defaultValue = "30")
    private int connectionTimeout = 30;
    @UriParam
    private String serverURIs;
    @UriParam
    private int mqttVersion;
    @UriParam(defaultValue = "true")
    private boolean automaticReconnect = true;
    @UriParam(defaultValue = "128000")
    private int maxReconnectDelay = 128000;
    @UriParam(label = "advanced")
    private Properties customWebSocketHeaders;
    @UriParam(label = "advanced", defaultValue = "1")
    private int executorServiceTimeout = 1;

    public String getClientId() {
        return clientId;
    }

    /**
     * MQTT client identifier. The identifier must be unique.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    /**
     * The URL of the MQTT broker.
     */
    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public int getQos() {
        return qos;
    }

    /**
     * Client quality of service level (0-2).
     */
    public void setQos(int qos) {
        this.qos = qos;
    }

    public boolean isRetained() {
        return retained;
    }

    /**
     * Retain option
     */
    public void setRetained(boolean retained) {
        this.retained = retained;
    }

    public PahoPersistence getPersistence() {
        return persistence;
    }

    /**
     * Client persistence to be used - memory or file.
     */
    public void setPersistence(PahoPersistence persistence) {
        this.persistence = persistence;
    }

    public String getFilePersistenceDirectory() {
        return filePersistenceDirectory;
    }

    /**
     * Base directory used by file persistence. Will by default use user directory.
     */
    public void setFilePersistenceDirectory(String filePersistenceDirectory) {
        this.filePersistenceDirectory = filePersistenceDirectory;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Username to be used for authentication against the MQTT broker
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to be used for authentication against the MQTT broker
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    /**
     * Sets the keep alive interval. This value, measured in seconds, defines the
     * maximum time interval between messages sent or received. It enables the
     * client to detect if the server is no longer available, without having to wait
     * for the TCP/IP timeout. The client will ensure that at least one message
     * travels across the network within each keep alive period. In the absence of a
     * data-related message during the time period, the client sends a very small
     * ping message, which the server will acknowledge. A value of 0 disables
     * keepalive processing in the client.
     * <p>
     * The default value is 60 seconds
     * </p>
     */
    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public int getMaxInflight() {
        return maxInflight;
    }

    /**
     * Sets the max inflight. please increase this value in a high traffic
     * environment.
     * <p>
     * The default value is 10
     * </p>
     */
    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public String getWillTopic() {
        return willTopic;
    }

    /**
     * Sets the "Last Will and Testament" (LWT) for the connection. In the event
     * that this client unexpectedly loses its connection to the server, the server
     * will publish a message to itself using the supplied details.
     *
     * The topic to publish to
     * The byte payload for the message.
     * The quality of service to publish the message at (0, 1 or 2).
     * Whether or not the message should be retained.
     */
    public void setWillTopic(String willTopic) {
        this.willTopic = willTopic;
    }

    public String getWillPayload() {
        return willPayload;
    }

    /**
     * Sets the "Last Will and Testament" (LWT) for the connection. In the event
     * that this client unexpectedly loses its connection to the server, the server
     * will publish a message to itself using the supplied details.
     *
     * The topic to publish to
     * The byte payload for the message.
     * The quality of service to publish the message at (0, 1 or 2).
     * Whether or not the message should be retained.
     */
    public void setWillPayload(String willPayload) {
        this.willPayload = willPayload;
    }

    public int getWillQos() {
        return willQos;
    }

    /**
     * Sets the "Last Will and Testament" (LWT) for the connection. In the event
     * that this client unexpectedly loses its connection to the server, the server
     * will publish a message to itself using the supplied details.
     *
     * The topic to publish to
     * The byte payload for the message.
     * The quality of service to publish the message at (0, 1 or 2).
     * Whether or not the message should be retained.
     */
    public void setWillQos(int willQos) {
        this.willQos = willQos;
    }

    public boolean isWillRetained() {
        return willRetained;
    }

    /**
     * Sets the "Last Will and Testament" (LWT) for the connection. In the event
     * that this client unexpectedly loses its connection to the server, the server
     * will publish a message to itself using the supplied details.
     *
     * The topic to publish to
     * The byte payload for the message.
     * The quality of service to publish the message at (0, 1 or 2).
     * Whether or not the message should be retained.
     */
    public void setWillRetained(boolean willRetained) {
        this.willRetained = willRetained;
    }

    public SocketFactory getSocketFactory() {
        return socketFactory;
    }

    /**
     * Sets the SocketFactory to use. This allows an application to
     * apply its own policies around the creation of network sockets. If using an
     * SSL connection, an SSLSocketFactory can be used to supply
     * application-specific security settings.
     */
    public void setSocketFactory(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    public Properties getSslClientProps() {
        return sslClientProps;
    }

    /**
     * Sets the SSL properties for the connection.
     * <p>
     * Note that these properties are only valid if an implementation of the Java
     * Secure Socket Extensions (JSSE) is available. These properties are
     * <em>not</em> used if a custom SocketFactory has been set.
     *
     * The following properties can be used:
     * </p>
     * <dl>
     * <dt>com.ibm.ssl.protocol</dt>
     * <dd>One of: SSL, SSLv3, TLS, TLSv1, SSL_TLS.</dd>
     * <dt>com.ibm.ssl.contextProvider
     * <dd>Underlying JSSE provider. For example "IBMJSSE2" or "SunJSSE"</dd>
     *
     * <dt>com.ibm.ssl.keyStore</dt>
     * <dd>The name of the file that contains the KeyStore object that you want the
     * KeyManager to use. For example /mydir/etc/key.p12</dd>
     *
     * <dt>com.ibm.ssl.keyStorePassword</dt>
     * <dd>The password for the KeyStore object that you want the KeyManager to use.
     * The password can either be in plain-text, or may be obfuscated using the
     * static method:
     * <code>com.ibm.micro.security.Password.obfuscate(char[] password)</code>. This
     * obfuscates the password using a simple and insecure XOR and Base64 encoding
     * mechanism. Note that this is only a simple scrambler to obfuscate clear-text
     * passwords.</dd>
     *
     * <dt>com.ibm.ssl.keyStoreType</dt>
     * <dd>Type of key store, for example "PKCS12", "JKS", or "JCEKS".</dd>
     *
     * <dt>com.ibm.ssl.keyStoreProvider</dt>
     * <dd>Key store provider, for example "IBMJCE" or "IBMJCEFIPS".</dd>
     *
     * <dt>com.ibm.ssl.trustStore</dt>
     * <dd>The name of the file that contains the KeyStore object that you want the
     * TrustManager to use.</dd>
     *
     * <dt>com.ibm.ssl.trustStorePassword</dt>
     * <dd>The password for the TrustStore object that you want the TrustManager to
     * use. The password can either be in plain-text, or may be obfuscated using the
     * static method:
     * <code>com.ibm.micro.security.Password.obfuscate(char[] password)</code>. This
     * obfuscates the password using a simple and insecure XOR and Base64 encoding
     * mechanism. Note that this is only a simple scrambler to obfuscate clear-text
     * passwords.</dd>
     *
     * <dt>com.ibm.ssl.trustStoreType</dt>
     * <dd>The type of KeyStore object that you want the default TrustManager to
     * use. Same possible values as "keyStoreType".</dd>
     *
     * <dt>com.ibm.ssl.trustStoreProvider</dt>
     * <dd>Trust store provider, for example "IBMJCE" or "IBMJCEFIPS".</dd>
     *
     * <dt>com.ibm.ssl.enabledCipherSuites</dt>
     * <dd>A list of which ciphers are enabled. Values are dependent on the
     * provider, for example:
     * SSL_RSA_WITH_AES_128_CBC_SHA;SSL_RSA_WITH_3DES_EDE_CBC_SHA.</dd>
     *
     * <dt>com.ibm.ssl.keyManager</dt>
     * <dd>Sets the algorithm that will be used to instantiate a KeyManagerFactory
     * object instead of using the default algorithm available in the platform.
     * Example values: "IbmX509" or "IBMJ9X509".</dd>
     *
     * <dt>com.ibm.ssl.trustManager</dt>
     * <dd>Sets the algorithm that will be used to instantiate a TrustManagerFactory
     * object instead of using the default algorithm available in the platform.
     * Example values: "PKIX" or "IBMJ9X509".</dd>
     * </dl>
     */
    public void setSslClientProps(Properties sslClientProps) {
        this.sslClientProps = sslClientProps;
    }

    public boolean isHttpsHostnameVerificationEnabled() {
        return httpsHostnameVerificationEnabled;
    }

    /**
     * Whether SSL HostnameVerifier is enabled or not.
     * The default value is true.
     */
    public void setHttpsHostnameVerificationEnabled(boolean httpsHostnameVerificationEnabled) {
        this.httpsHostnameVerificationEnabled = httpsHostnameVerificationEnabled;
    }

    public HostnameVerifier getSslHostnameVerifier() {
        return sslHostnameVerifier;
    }

    /**
     * Sets the HostnameVerifier for the SSL connection. Note that it will be used
     * after handshake on a connection and you should do actions by yourself when
     * hostname is verified error.
     * <p>
     * There is no default HostnameVerifier
     * </p>
     */
    public void setSslHostnameVerifier(HostnameVerifier sslHostnameVerifier) {
        this.sslHostnameVerifier = sslHostnameVerifier;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    /**
     * Sets whether the client and server should remember state across restarts and
     * reconnects.
     * <ul>
     * <li>If set to false both the client and server will maintain state across
     * restarts of the client, the server and the connection. As state is
     * maintained:
     * <ul>
     * <li>Message delivery will be reliable meeting the specified QOS even if the
     * client, server or connection are restarted.
     * <li>The server will treat a subscription as durable.
     * </ul>
     * <li>If set to true the client and server will not maintain state across
     * restarts of the client, the server or the connection. This means
     * <ul>
     * <li>Message delivery to the specified QOS cannot be maintained if the client,
     * server or connection are restarted
     * <li>The server will treat a subscription as non-durable
     * </ul>
     * </ul>
     */
    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout value. This value, measured in seconds, defines
     * the maximum time interval the client will wait for the network connection to
     * the MQTT server to be established. The default timeout is 30 seconds. A value
     * of 0 disables timeout processing meaning the client will wait until the
     * network connection is made successfully or fails.
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getServerURIs() {
        return serverURIs;
    }

    /**
     * Set a list of one or more serverURIs the client may connect to.
     * Multiple servers can be separated by comma.
     * <p>
     * Each <code>serverURI</code> specifies the address of a server that the client
     * may connect to. Two types of connection are supported <code>tcp://</code> for
     * a TCP connection and <code>ssl://</code> for a TCP connection secured by
     * SSL/TLS. For example:
     * <ul>
     * <li><code>tcp://localhost:1883</code></li>
     * <li><code>ssl://localhost:8883</code></li>
     * </ul>
     * If the port is not specified, it will default to 1883 for
     * <code>tcp://</code>" URIs, and 8883 for <code>ssl://</code> URIs.
     * <p>
     * If serverURIs is set then it overrides the serverURI parameter passed in on
     * the constructor of the MQTT client.
     * <p>
     * When an attempt to connect is initiated the client will start with the first
     * serverURI in the list and work through the list until a connection is
     * established with a server. If a connection cannot be made to any of the
     * servers then the connect attempt fails.
     * <p>
     * Specifying a list of servers that a client may connect to has several uses:
     * <ol>
     * <li>High Availability and reliable message delivery
     * <p>
     * Some MQTT servers support a high availability feature where two or more
     * "equal" MQTT servers share state. An MQTT client can connect to any of the
     * "equal" servers and be assured that messages are reliably delivered and
     * durable subscriptions are maintained no matter which server the client
     * connects to.
     * </p>
     * <p>
     * The cleansession flag must be set to false if durable subscriptions and/or
     * reliable message delivery is required.
     * </p>
     * </li>
     * <li>Hunt List
     * <p>
     * A set of servers may be specified that are not "equal" (as in the high
     * availability option). As no state is shared across the servers reliable
     * message delivery and durable subscriptions are not valid. The cleansession
     * flag must be set to true if the hunt list mode is used
     * </p>
     * </li>
     * </ol>
     */
    public void setServerURIs(String serverURIs) {
        this.serverURIs = serverURIs;
    }

    public int getMqttVersion() {
        return mqttVersion;
    }

    /**
     * Sets the MQTT version. The default action is to connect with version 3.1.1,
     * and to fall back to 3.1 if that fails. Version 3.1.1 or 3.1 can be selected
     * specifically, with no fall back, by using the MQTT_VERSION_3_1_1 or
     * MQTT_VERSION_3_1 options respectively.
     */
    public void setMqttVersion(int mqttVersion) {
        this.mqttVersion = mqttVersion;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    /**
     * Sets whether the client will automatically attempt to reconnect to the server
     * if the connection is lost.
     * <ul>
     * <li>If set to false, the client will not attempt to automatically reconnect
     * to the server in the event that the connection is lost.</li>
     * <li>If set to true, in the event that the connection is lost, the client will
     * attempt to reconnect to the server. It will initially wait 1 second before it
     * attempts to reconnect, for every failed reconnect attempt, the delay will
     * double until it is at 2 minutes at which point the delay will stay at 2
     * minutes.</li>
     * </ul>
     */
    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public int getMaxReconnectDelay() {
        return maxReconnectDelay;
    }

    /**
     * Get the maximum time (in millis) to wait between reconnects
     */
    public void setMaxReconnectDelay(int maxReconnectDelay) {
        this.maxReconnectDelay = maxReconnectDelay;
    }

    public Properties getCustomWebSocketHeaders() {
        return customWebSocketHeaders;
    }

    /**
     * Sets the Custom WebSocket Headers for the WebSocket Connection.
     */
    public void setCustomWebSocketHeaders(Properties customWebSocketHeaders) {
        this.customWebSocketHeaders = customWebSocketHeaders;
    }

    public int getExecutorServiceTimeout() {
        return executorServiceTimeout;
    }

    /**
     * Set the time in seconds that the executor service should wait when
     * terminating before forcefully terminating. It is not recommended to change
     * this value unless you are absolutely sure that you need to.
     */
    public void setExecutorServiceTimeout(int executorServiceTimeout) {
        this.executorServiceTimeout = executorServiceTimeout;
    }

    public PahoConfiguration copy() {
        try {
            return (PahoConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }


}
