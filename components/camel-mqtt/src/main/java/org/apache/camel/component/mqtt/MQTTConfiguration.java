/**
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
package org.apache.camel.component.mqtt;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.transport.TcpTransport;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;

@UriParams
public class MQTTConfiguration extends MQTT {
    public static final String MQTT_SUBSCRIBE_TOPIC = "CamelMQTTSubscribeTopic";
    public static final String MQTT_PUBLISH_TOPIC = "CamelMQTTPublishTopic";
    
    // inherited options from MQTT
    @UriParam(defaultValue = "tcp://127.0.0.1:1883")
    URI host;
    @UriParam
    URI localAddress;
    @UriParam
    SSLContext sslContext;
    @UriParam
    DispatchQueue dispatchQueue;
    @UriParam
    Executor blockingExecutor;
    @UriParam
    int maxReadRate;
    @UriParam
    int maxWriteRate;
    @UriParam(defaultValue = "" + TcpTransport.IPTOS_THROUGHPUT)
    int trafficClass = TcpTransport.IPTOS_THROUGHPUT;
    @UriParam(defaultValue = "" + 1024 * 64)
    int receiveBufferSize = 1024 * 64;
    @UriParam(defaultValue = "" + 1024 * 64)
    int sendBufferSize = 1024 * 64;
    @UriParam(defaultValue = "10")
    long reconnectDelay = 10;
    @UriParam(defaultValue = "" + 30 * 1000)
    long reconnectDelayMax = 30 * 1000;
    @UriParam(defaultValue =  "2.0")
    double reconnectBackOffMultiplier = 2.0f;
    @UriParam(defaultValue = "-1")
    long reconnectAttemptsMax = -1;
    @UriParam(defaultValue = "-1")
    long connectAttemptsMax = -1;
    @UriParam
    String clientId;
    @UriParam
    boolean cleanSession;
    @UriParam
    short keepAlive;
    @UriParam
    String willTopic;
    @UriParam
    String willMessage;
    @UriParam(enums = "AtMostOnce,AtLeastOne,ExactlyOnce", defaultValue = "AtMostOnce")
    QoS willQos = QoS.AT_MOST_ONCE;
    @UriParam
    QoS willRetain;
    @UriParam(defaultValue = "3.1")
    String version;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean lazySessionCreation = true;

    /**
     * These a properties that are looked for in an Exchange - to publish to
     */
    @UriParam(defaultValue = "MQTTTopicPropertyName")
    private String mqttTopicPropertyName = "MQTTTopicPropertyName";
    @UriParam(defaultValue = "MQTTRetain")
    private String mqttRetainPropertyName = "MQTTRetain";
    @UriParam(defaultValue = "MQTTQos")
    private String mqttQosPropertyName = "MQTTQos";

    /**
     * These are set on the Endpoint - together with properties inherited from MQTT
     */
    @UriParam
    @Deprecated
    private String subscribeTopicName = "";
    @UriParam
    private String subscribeTopicNames = "";
    @UriParam(defaultValue = "camel/mqtt/test")
    private String publishTopicName = "camel/mqtt/test";
    @UriParam(defaultValue = "10")
    private int connectWaitInSeconds = 10;
    @UriParam(defaultValue = "5")
    private int disconnectWaitInSeconds = 5;
    @UriParam(defaultValue = "5")
    private int sendWaitInSeconds = 5;
    @UriParam
    private boolean byDefaultRetain;
    @UriParam(enums = "AtMostOnce,AtLeastOne,ExactlyOnce", defaultValue = "AtLeastOnce")
    private String qualityOfService = QoS.AT_LEAST_ONCE.name();
    private QoS qos = QoS.AT_LEAST_ONCE;

    public String getQualityOfService() {
        return qualityOfService;
    }

    /**
     * Quality of service level to use for topics.
     */
    public void setQualityOfService(String qualityOfService) {
        this.qos = getQoS(qualityOfService);
        this.qualityOfService = qualityOfService;
    }

    public QoS getQoS() {
        return qos;
    }

    @Deprecated
    public String getSubscribeTopicName() {
        return subscribeTopicName;
    }

    /**
     * The name of the Topic to subscribe to for messages.
     */
    @Deprecated
    public void setSubscribeTopicName(String subscribeTopicName) {
        this.subscribeTopicName = subscribeTopicName;
    }

    public String getSubscribeTopicNames() {
        return subscribeTopicNames;
    }

    /**
     * A comma-delimited list of Topics to subscribe to for messages.
     * Note that each item of this list can contain MQTT wildcards (+ and/or #), in order to subscribe
     * to topics matching a certain pattern within a hierarchy.
     * For example, + is a wildcard for all topics at a level within the hierarchy,
     * so if a broker has topics topics/one and topics/two, then topics/+ can be used to subscribe to both.
     * A caveat to consider here is that if the broker adds topics/three, the route would also begin to receive messages from that topic.
     */
    public void setSubscribeTopicNames(String subscribeTopicNames) {
        this.subscribeTopicNames = subscribeTopicNames;
    }

    public String getPublishTopicName() {
        return publishTopicName;
    }

    /**
     * The default Topic to publish messages on
     */
    public void setPublishTopicName(String publishTopicName) {
        this.publishTopicName = publishTopicName;
    }

    /**
     * Please use MQTT_SUBSCRIBE_TOPIC and MQTT_PUBLISH_TOPIC to set or get the topic name
     */
    @Deprecated
    public String getMqttTopicPropertyName() {
        return mqttTopicPropertyName;
    }

    /**
     * Please use MQTT_SUBSCRIBE_TOPIC and MQTT_PUBLISH_TOPIC to set or get the topic name
     */
    @Deprecated
    public void setMqttTopicPropertyName(String mqttTopicPropertyName) {
        this.mqttTopicPropertyName = mqttTopicPropertyName;
    }

    public String getMqttRetainPropertyName() {
        return mqttRetainPropertyName;
    }

    /**
     * The property name to look for on an Exchange for an individual published message.
     * If this is set (expects a Boolean value) - then the retain property will be set on the message sent to the MQTT message broker.
     */
    public void setMqttRetainPropertyName(String mqttRetainPropertyName) {
        this.mqttRetainPropertyName = mqttRetainPropertyName;
    }

    public String getMqttQosPropertyName() {
        return mqttQosPropertyName;
    }

    /**
     * The property name to look for on an Exchange for an individual published message.
     * If this is set (one of AtMostOnce, AtLeastOnce or ExactlyOnce ) - then that QoS will be set on the message sent to the MQTT message broker.
     */
    public void setMqttQosPropertyName(String mqttQosPropertyName) {
        this.mqttQosPropertyName = mqttQosPropertyName;
    }

    public int getConnectWaitInSeconds() {
        return connectWaitInSeconds;
    }

    /**
     * Delay in seconds the Component will wait for a connection to be established to the MQTT broker
     */
    public void setConnectWaitInSeconds(int connectWaitInSeconds) {
        this.connectWaitInSeconds = connectWaitInSeconds;
    }

    public int getDisconnectWaitInSeconds() {
        return disconnectWaitInSeconds;
    }

    /**
     * The number of seconds the Component will wait for a valid disconnect on stop() from the MQTT broker
     */
    public void setDisconnectWaitInSeconds(int disconnectWaitInSeconds) {
        this.disconnectWaitInSeconds = disconnectWaitInSeconds;
    }

    public int getSendWaitInSeconds() {
        return sendWaitInSeconds;
    }

    /**
     * The maximum time the Component will wait for a receipt from the MQTT broker to acknowledge a published message before throwing an exception
     */
    public void setSendWaitInSeconds(int sendWaitInSeconds) {
        this.sendWaitInSeconds = sendWaitInSeconds;
    }

    public boolean isByDefaultRetain() {
        return byDefaultRetain;
    }

    /**
     * The default retain policy to be used on messages sent to the MQTT broker
     */
    public void setByDefaultRetain(boolean byDefaultRetain) {
        this.byDefaultRetain = byDefaultRetain;
    }

    static QoS getQoS(String qualityOfService) {
        for (QoS q : QoS.values()) {
            if (q.name().equalsIgnoreCase(qualityOfService)) {
                return q;
            }
        }
        if (qualityOfService.equalsIgnoreCase("ATMOSTONCE")) {
            return QoS.AT_MOST_ONCE;
        }
        if (qualityOfService.equalsIgnoreCase("EXACTLYONCE")) {
            return QoS.EXACTLY_ONCE;
        }
        if (qualityOfService.equalsIgnoreCase("ATLEASTONCE")) {
            return QoS.AT_LEAST_ONCE;
        }
        throw new IllegalArgumentException("There is no QoS with name " + qualityOfService);
    }

    /**
     *  Use to set the client Id of the session.
     *  This is what an MQTT server uses to identify a session where setCleanSession(false); is being used.
     *  The id must be 23 characters or less. Defaults to auto generated id (based on your socket address, port and timestamp).
     */
    @Override
    public void setClientId(String clientId) {
        super.setClientId(clientId);
    }

    /**
     * Set to false if you want the MQTT server to persist topic subscriptions and ack positions across client sessions. Defaults to true.
     */
    @Override
    public void setCleanSession(boolean cleanSession) {
        super.setCleanSession(cleanSession);
    }

    /**
     * Configures the Keep Alive timer in seconds. Defines the maximum time interval between messages received from a client.
     * It enables the server to detect that the network connection to a client has dropped, without having to wait for the long TCP/IP timeout.
     */
    @Override
    public void setKeepAlive(short keepAlive) {
        super.setKeepAlive(keepAlive);
    }

    /**
     * Password to be used for authentication against the MQTT broker
     */
    @Override
    public void setPassword(String password) {
        super.setPassword(password);
    }

    /**
     * Username to be used for authentication against the MQTT broker
     */
    @Override
    public void setUserName(String userName) {
        super.setUserName(userName);
    }

    /**
     * The Will message to send. Defaults to a zero length message.
     */
    @Override
    public void setWillMessage(String willMessage) {
        super.setWillMessage(willMessage);
    }

    /**
     * Sets the quality of service to use for the Will message. Defaults to AT_MOST_ONCE.
     */
    @Override
    public void setWillQos(QoS willQos) {
        super.setWillQos(willQos);
    }

    /**
     * Set to 3.1.1 to use MQTT version 3.1.1. Otherwise defaults to the 3.1 protocol version.
     */
    @Override
    public void setVersion(String version) {
        super.setVersion(version);
    }

    @Override
    public String getVersion() {
        return super.getVersion();
    }

    /**
     * Set to true if you want the Will to be published with the retain option.
     */
    @Override
    public void setWillRetain(boolean willRetain) {
        super.setWillRetain(willRetain);
    }

    /**
     * If set the server will publish the client's Will message to the specified topics if the client has an unexpected disconnection.
     */
    @Override
    public void setWillTopic(String willTopic) {
        super.setWillTopic(willTopic);
    }

    @Override
    public Executor getBlockingExecutor() {
        return super.getBlockingExecutor();
    }

    /**
     * SSL connections perform blocking operations against internal thread pool unless you call the setBlockingExecutor method to configure that executor they will use instead.
     */
    @Override
    public void setBlockingExecutor(Executor blockingExecutor) {
        super.setBlockingExecutor(blockingExecutor);
    }

    @Override
    public DispatchQueue getDispatchQueue() {
        return super.getDispatchQueue();
    }

    /**
     * A HawtDispatch dispatch queue is used to synchronize access to the connection.
     * If an explicit queue is not configured via the setDispatchQueue method, then a new queue will be created for the connection.
     * Setting an explicit queue might be handy if you want multiple connection to share the same queue for synchronization.
     */
    @Override
    public void setDispatchQueue(DispatchQueue dispatchQueue) {
        super.setDispatchQueue(dispatchQueue);
    }

    @Override
    public URI getLocalAddress() {
        return super.getLocalAddress();
    }

    /**
     * The local InetAddress and port to use
     */
    @Override
    public void setLocalAddress(String localAddress) throws URISyntaxException {
        super.setLocalAddress(localAddress);
    }

    @Override
    public void setLocalAddress(URI localAddress) {
        super.setLocalAddress(localAddress);
    }

    @Override
    public int getMaxReadRate() {
        return super.getMaxReadRate();
    }

    /**
     * Sets the maximum bytes per second that this transport will receive data at.
     * This setting throttles reads so that the rate is not exceeded. Defaults to 0 which disables throttling.
     */
    @Override
    public void setMaxReadRate(int maxReadRate) {
        super.setMaxReadRate(maxReadRate);
    }

    @Override
    public int getMaxWriteRate() {
        return super.getMaxWriteRate();
    }

    /**
     * Sets the maximum bytes per second that this transport will send data at.
     * This setting throttles writes so that the rate is not exceeded. Defaults to 0 which disables throttling.
     */
    @Override
    public void setMaxWriteRate(int maxWriteRate) {
        super.setMaxWriteRate(maxWriteRate);
    }

    @Override
    public int getReceiveBufferSize() {
        return super.getReceiveBufferSize();
    }

    /**
     * Sets the size of the internal socket receive buffer. Defaults to 65536 (64k)
     */
    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        super.setReceiveBufferSize(receiveBufferSize);
    }

    @Override
    public URI getHost() {
        return super.getHost();
    }

    /**
     * The URI of the MQTT broker to connect too - this component also supports SSL - e.g. ssl://127.0.0.1:8883
     */
    @Override
    public void setHost(String host) throws URISyntaxException {
        super.setHost(host);
    }

    /**
     * The URI of the MQTT broker to connect too - this component also supports SSL - e.g. ssl://127.0.0.1:8883
     */
    @Override
    public void setHost(URI host) {
        super.setHost(host);
    }

    @Override
    public int getSendBufferSize() {
        return super.getSendBufferSize();
    }

    /**
     *  Sets the size of the internal socket send buffer. Defaults to 65536 (64k)
     */
    @Override
    public void setSendBufferSize(int sendBufferSize) {
        super.setSendBufferSize(sendBufferSize);
    }

    @Override
    public SSLContext getSslContext() {
        return super.getSslContext();
    }

    /**
     * To configure security using SSLContext configuration
     */
    @Override
    public void setSslContext(SSLContext sslContext) {
        super.setSslContext(sslContext);
    }

    @Override
    public int getTrafficClass() {
        return super.getTrafficClass();
    }

    /**
     * Sets traffic class or type-of-service octet in the IP header for packets sent from the transport.
     * Defaults to 8 which means the traffic should be optimized for throughput.
     */
    @Override
    public void setTrafficClass(int trafficClass) {
        super.setTrafficClass(trafficClass);
    }

    @Override
    public long getConnectAttemptsMax() {
        return super.getConnectAttemptsMax();
    }

    /**
     * The maximum number of reconnect attempts before an error is reported back to the client on the first attempt
     * by the client to connect to a server. Set to -1 to use unlimited attempts. Defaults to -1.
     */
    @Override
    public void setConnectAttemptsMax(long connectAttemptsMax) {
        super.setConnectAttemptsMax(connectAttemptsMax);
    }

    @Override
    public long getReconnectAttemptsMax() {
        return super.getReconnectAttemptsMax();
    }

    /**
     * The maximum number of reconnect attempts before an error is reported back to the client after a server
     * connection had previously been established. Set to -1 to use unlimited attempts. Defaults to -1.
     */
    @Override
    public void setReconnectAttemptsMax(long reconnectAttemptsMax) {
        super.setReconnectAttemptsMax(reconnectAttemptsMax);
    }

    @Override
    public double getReconnectBackOffMultiplier() {
        return super.getReconnectBackOffMultiplier();
    }

    /**
     * The Exponential backoff be used between reconnect attempts. Set to 1 to disable exponential backoff. Defaults to 2.
     */
    @Override
    public void setReconnectBackOffMultiplier(double reconnectBackOffMultiplier) {
        super.setReconnectBackOffMultiplier(reconnectBackOffMultiplier);
    }

    @Override
    public long getReconnectDelay() {
        return super.getReconnectDelay();
    }

    /**
     * How long to wait in ms before the first reconnect attempt. Defaults to 10.
     */
    @Override
    public void setReconnectDelay(long reconnectDelay) {
        super.setReconnectDelay(reconnectDelay);
    }

    @Override
    public long getReconnectDelayMax() {
        return super.getReconnectDelayMax();
    }

    /**
     * The maximum amount of time in ms to wait between reconnect attempts. Defaults to 30,000.
     */
    @Override
    public void setReconnectDelayMax(long reconnectDelayMax) {
        super.setReconnectDelayMax(reconnectDelayMax);
    }

    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    /**
     * Sessions can be lazily created to avoid exceptions, if the remote server is not up and running when the Camel producer is started.
     */
    public void setLazySessionCreation(boolean lazySessionCreation) {
        this.lazySessionCreation = lazySessionCreation;
    }
}



