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

    @UriParam(defaultValue = "tcp://localhost:1883",
              description = "The URL of the broker: tcp://host:port or mqtt://host:port for a plain connection,"
                            + " ssl://host:port or mqtts://host:port for TLS. Without a port, 1883 is used for a plain"
                            + " connection and 8883 for TLS. The URL must not hold user information, a path or a query: the"
                            + " username and password options carry the credentials.")
    private String brokerUrl = "tcp://localhost:1883";
    @UriParam(defaultValue = "MQTT_5_0",
              description = "The version of the MQTT protocol that the client speaks with the broker. With MQTT_3_1_1, the"
                            + " sessionExpiryInterval and receiveMaximum options and the MQTT 5 headers are not used.")
    private VertxMqttVersion mqttVersion = VertxMqttVersion.MQTT_5_0;
    @UriParam(description = "The client identifier of the connection. If not set, a random UUID is used, and a new one"
                            + " every time the connection starts again, so a session that the broker keeps (cleanStart"
                            + " false) needs a clientId. The broker allows one connection per client identifier, so two"
                            + " endpoints must not share one.")
    private String clientId;
    @UriParam(defaultValue = "true",
              description = "Whether the broker discards the session of the client when it connects: the Clean Start flag"
                            + " of MQTT 5, the Clean Session flag of MQTT 3.1.1. If false, the broker keeps the"
                            + " subscriptions and the queued messages of the client, and the consumer does not unsubscribe"
                            + " when it stops. With MQTT 5 the session also needs a sessionExpiryInterval above 0 to"
                            + " outlive the connection.")
    private boolean cleanStart = true;
    @UriParam(defaultValue = "1", enums = "0,1,2",
              description = "The quality of service: 0 (at most once), 1 (at least once) or 2 (exactly once). The producer"
                            + " publishes with it, and the CamelVertxMqttOverrideQos header replaces it. The consumer"
                            + " subscribes with it, as the highest quality of service that the broker delivers messages"
                            + " with.")
    private int qos = 1;
    @UriParam(defaultValue = "false",
              description = "Whether the producer publishes messages as retained, so that the broker keeps the last one of"
                            + " the topic for subscribers that arrive later. The CamelVertxMqttOverrideRetained header"
                            + " replaces this option.")
    private boolean retained;
    /**
     * The keep alive interval in seconds. 0 switches keep alive off.
     */
    @UriParam(defaultValue = "30")
    private int keepAliveInterval = 30;
    @UriParam(label = "advanced", defaultValue = "30000", javaType = "java.time.Duration",
              description = "How long to wait for a connection to the broker, in milliseconds. It bounds every connection"
                            + " attempt, and how long the producer waits for the connection before the exchange fails.")
    private long connectTimeout = 30_000;
    /**
     * MQTT 5 only. How long the broker keeps the session after the connection closed, in seconds. 0 ends it with the
     * connection.
     */
    @UriParam(defaultValue = "0")
    private long sessionExpiryInterval;
    @UriParam(label = "consumer", defaultValue = "0",
              description = "MQTT 5 only. The most messages of quality of service 1 and 2 that the broker sends to the"
                            + " client without an acknowledgement. 0 does not send the Receive Maximum property, and MQTT 5"
                            + " then allows 65535.")
    private int receiveMaximum;
    /**
     * The largest message in bytes the client receives, the protocol maximum by default. Every message is read into
     * memory as a whole, so lower it for a broker whose publishers are not trusted.
     */
    @UriParam(label = "advanced", defaultValue = "268435455")
    private int maxMessageSize = VertxMqttConstants.MAX_MESSAGE_SIZE;
    @UriParam(label = "producer", defaultValue = "10",
              description = "The most messages of quality of service 1 and 2 that the producer has sent without an"
                            + " acknowledgement from the broker. The exchange of a message beyond it fails at once.")
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
    @UriParam(label = "advanced",
              description = "The payload of the will message, sent as UTF-8 bytes. Only used when willTopic is set.")
    private String willPayload;
    @UriParam(label = "advanced", defaultValue = "0", enums = "0,1,2",
              description = "The quality of service of the will message: 0, 1 or 2. Only used when willTopic is set.")
    private int willQos;
    @UriParam(label = "advanced", defaultValue = "false",
              description = "Whether the broker publishes the will message as retained. Only used when willTopic is set.")
    private boolean willRetained;
    @UriParam(defaultValue = "true",
              description = "Whether the client connects again when the connection is lost, and keeps trying in the"
                            + " background when the first connection fails. If false, a failed first connection fails the"
                            + " start of the consumer or producer, and a lost connection is not connected again.")
    private boolean automaticReconnect = true;
    @UriParam(label = "advanced", defaultValue = "1000", javaType = "java.time.Duration",
              description = "The delay before the first attempt to reconnect, in milliseconds. Every further attempt"
                            + " doubles the delay, up to maxReconnectDelay, and adds up to a fifth of it at random. A"
                            + " successful connection starts again from this delay.")
    private long reconnectDelay = 1000;
    @UriParam(label = "advanced", defaultValue = "60000", javaType = "java.time.Duration",
              description = "The longest delay between two attempts to reconnect, in milliseconds, before the random part"
                            + " of up to a fifth is added.")
    private long maxReconnectDelay = 60_000;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer acknowledges a message of quality of service 1 or 2 only once its"
                            + " exchange completed successfully, instead of as soon as it arrives. When the exchange of a"
                            + " message of quality of service 1 fails, the consumer reconnects so that the broker delivers"
                            + " it again, which needs automaticReconnect and a session that outlives the connection"
                            + " (cleanStart false, and with MQTT 5 a sessionExpiryInterval above 0). Otherwise the failed"
                            + " message is acknowledged and not delivered again.")
    private boolean manualAcksEnabled;
    @UriParam(label = "consumer,advanced", defaultValue = "1000",
              description = "The most received messages whose exchanges have not completed. When it is reached, the"
                            + " consumer stops reading from the connection until half of them completed, which holds back"
                            + " the broker for every quality of service.")
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
