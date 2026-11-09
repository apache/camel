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
package org.apache.camel.component.vertx.mqtt;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * The options of a Vert.x MQTT endpoint, shared by the component and copied for every endpoint.
 */
@UriParams
@Configurer
public class VertxMqttConfiguration implements Cloneable {

    /**
     * The URL of the broker: tcp://host:port or mqtt://host:port for a plain connection, ssl://host:port or
     * mqtts://host:port for TLS. Without a port 1883 is used for a plain connection and 8883 for TLS.
     */
    @UriParam(defaultValue = "tcp://localhost:1883")
    private String brokerUrl = "tcp://localhost:1883";
    /**
     * The version of the MQTT protocol.
     */
    @UriParam(defaultValue = "MQTT_5_0")
    private VertxMqttVersion mqttVersion = VertxMqttVersion.MQTT_5_0;
    /**
     * The client identifier. A random one is generated if none is set. A broker allows one connection per client
     * identifier, so two endpoints must not share one.
     */
    @UriParam
    private String clientId;
    /**
     * Whether the broker discards the session of the client when it connects, the clean session flag of MQTT 3.1.1.
     */
    @UriParam(defaultValue = "true")
    private boolean cleanStart = true;
    /**
     * The quality of service: 0 (at most once), 1 (at least once) or 2 (exactly once). For a consumer the maximum
     * quality of service of its subscription.
     */
    @UriParam(defaultValue = "1", enums = "0,1,2")
    private int qos = 1;
    /**
     * Whether the broker retains a published message for subscribers that arrive later.
     */
    @UriParam(defaultValue = "false")
    private boolean retained;
    /**
     * The keep alive interval in seconds. 0 switches keep alive off.
     */
    @UriParam(defaultValue = "30")
    private int keepAliveInterval = 30;
    /**
     * How long to wait for the connection to the broker, in milliseconds.
     */
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration")
    private long connectTimeout = 30_000;
    /**
     * MQTT 5 only. How long the broker keeps the session after the connection closed, in seconds. 0 ends it with the
     * connection.
     */
    @UriParam(defaultValue = "0")
    private long sessionExpiryInterval;
    /**
     * MQTT 5 only. The most messages of quality of service 1 and 2 the broker sends before the client acknowledges
     * them. 0 leaves it to the broker.
     */
    @UriParam(label = "consumer", defaultValue = "0")
    private int receiveMaximum;
    /**
     * The largest message in bytes the client receives, the protocol maximum by default. Every message is read into
     * memory as a whole, so lower it for a broker whose publishers are not trusted.
     */
    @UriParam(label = "advanced", defaultValue = "268435455")
    private int maxMessageSize = VertxMqttConstants.MAX_MESSAGE_SIZE;
    /**
     * The most messages of quality of service 1 and 2 the client has sent without an acknowledgement. A message beyond
     * it fails.
     */
    @UriParam(label = "producer", defaultValue = "10")
    private int maxInflightQueue = 10;
    /**
     * How long a message of quality of service 1 or 2 waits for its acknowledgement, in seconds. The exchange fails
     * when the time is up. 0 or less waits without a limit.
     */
    @UriParam(label = "producer", defaultValue = "30")
    private int ackTimeout = 30;
    /**
     * The topic of the will message the broker publishes when the client goes away without disconnecting.
     */
    @UriParam(label = "advanced")
    private String willTopic;
    /**
     * The payload of the will message.
     */
    @UriParam(label = "advanced")
    private String willPayload;
    /**
     * The quality of service of the will message.
     */
    @UriParam(label = "advanced", defaultValue = "0", enums = "0,1,2")
    private int willQos;
    /**
     * Whether the will message is retained.
     */
    @UriParam(label = "advanced", defaultValue = "false")
    private boolean willRetained;
    /**
     * Whether the client connects again when the connection is lost, and keeps trying when the first connection fails.
     * Without, a failed first connection fails the start of the route.
     */
    @UriParam(defaultValue = "true")
    private boolean automaticReconnect = true;
    /**
     * The delay before the first attempt to reconnect, in milliseconds. Every further attempt doubles it, up to the
     * maximum reconnect delay.
     */
    @UriParam(label = "advanced", defaultValue = "1000", javaType = "java.time.Duration")
    private long reconnectDelay = 1000;
    /**
     * The longest delay between two attempts to reconnect, in milliseconds.
     */
    @UriParam(label = "advanced", defaultValue = "60000", javaType = "java.time.Duration")
    private long maxReconnectDelay = 60_000;
    /**
     * Whether a message of quality of service 1 or 2 is acknowledged only once its exchange completed successfully.
     * Otherwise the client acknowledges it as soon as it arrives.
     */
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean manualAcksEnabled;
    /**
     * The most received messages waiting for their exchange to complete. Beyond it the client stops reading from the
     * connection until the consumer caught up, which holds back the broker for every quality of service.
     */
    @UriParam(label = "consumer,advanced", defaultValue = "1000")
    private int maxPendingMessages = 1000;
    /**
     * The user name to authenticate with.
     */
    @UriParam(label = "security", security = "secret")
    private String username;
    /**
     * The password to authenticate with.
     */
    @UriParam(label = "security", security = "secret")
    private String password;
    /**
     * The TLS configuration of a connection to an ssl:// or mqtts:// broker: key managers for a client certificate,
     * trust managers, protocols and cipher suites. Without it the trust store of the JVM is used. The host name of the
     * broker is always verified against its certificate: there is intentionally no option to switch the check off.
     */
    @UriParam(label = "security")
    @Metadata(javaType = "org.apache.camel.support.jsse.SSLContextParameters")
    private SSLContextParameters sslContextParameters;

    /**
     * @return a copy for an endpoint, so that its options do not change the component
     */
    public VertxMqttConfiguration copy() {
        try {
            return (VertxMqttConfiguration) clone();
        } catch (final CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(final String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public VertxMqttVersion getMqttVersion() {
        return mqttVersion;
    }

    public void setMqttVersion(final VertxMqttVersion mqttVersion) {
        this.mqttVersion = mqttVersion;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    public boolean isCleanStart() {
        return cleanStart;
    }

    public void setCleanStart(final boolean cleanStart) {
        this.cleanStart = cleanStart;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(final int qos) {
        this.qos = qos;
    }

    public boolean isRetained() {
        return retained;
    }

    public void setRetained(final boolean retained) {
        this.retained = retained;
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(final int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(final long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getSessionExpiryInterval() {
        return sessionExpiryInterval;
    }

    public void setSessionExpiryInterval(final long sessionExpiryInterval) {
        this.sessionExpiryInterval = sessionExpiryInterval;
    }

    public int getReceiveMaximum() {
        return receiveMaximum;
    }

    public void setReceiveMaximum(final int receiveMaximum) {
        this.receiveMaximum = receiveMaximum;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(final int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getMaxInflightQueue() {
        return maxInflightQueue;
    }

    public void setMaxInflightQueue(final int maxInflightQueue) {
        this.maxInflightQueue = maxInflightQueue;
    }

    public int getAckTimeout() {
        return ackTimeout;
    }

    public void setAckTimeout(final int ackTimeout) {
        this.ackTimeout = ackTimeout;
    }

    public String getWillTopic() {
        return willTopic;
    }

    public void setWillTopic(final String willTopic) {
        this.willTopic = willTopic;
    }

    public String getWillPayload() {
        return willPayload;
    }

    public void setWillPayload(final String willPayload) {
        this.willPayload = willPayload;
    }

    public int getWillQos() {
        return willQos;
    }

    public void setWillQos(final int willQos) {
        this.willQos = willQos;
    }

    public boolean isWillRetained() {
        return willRetained;
    }

    public void setWillRetained(final boolean willRetained) {
        this.willRetained = willRetained;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(final boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(final long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public long getMaxReconnectDelay() {
        return maxReconnectDelay;
    }

    public void setMaxReconnectDelay(final long maxReconnectDelay) {
        this.maxReconnectDelay = maxReconnectDelay;
    }

    public boolean isManualAcksEnabled() {
        return manualAcksEnabled;
    }

    public void setManualAcksEnabled(final boolean manualAcksEnabled) {
        this.manualAcksEnabled = manualAcksEnabled;
    }

    public int getMaxPendingMessages() {
        return maxPendingMessages;
    }

    public void setMaxPendingMessages(final int maxPendingMessages) {
        this.maxPendingMessages = maxPendingMessages;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(final SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
}
