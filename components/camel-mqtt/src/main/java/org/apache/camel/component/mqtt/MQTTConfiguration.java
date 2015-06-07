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
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.transport.TcpTransport;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Tracer;

@UriParams
public class MQTTConfiguration extends MQTT {
    public static final String MQTT_SUBSCRIBE_TOPIC = "CamelMQTTSubscribeTopic";
    public static final String MQTT_PUBLISH_TOPIC = "CamelMQTTPublishTopic";
    
    // inherited options from MQTT
    @UriParam
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
    @UriParam(defaultValue = "true")
    boolean useLocalHost = true;
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
    Tracer tracer;

    /**
     * These a properties that are looked for in an Exchange - to publish to
     */
    private String mqttTopicPropertyName = "MQTTTopicPropertyName";
    private String mqttRetainPropertyName = "MQTTRetain";
    private String mqttQosPropertyName = "MQTTQos";

    /**
     * These are set on the Endpoint - together with properties inherited from MQTT
     */
    @UriParam
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
    @UriParam
    private QoS qos = QoS.AT_LEAST_ONCE;
    private String qualityOfService = QoS.AT_LEAST_ONCE.name();

    public String getQualityOfService() {
        return qualityOfService;
    }

    public void setQualityOfService(String qualityOfService) {
        this.qos = getQoS(qualityOfService);
        this.qualityOfService = qualityOfService;
    }

    public QoS getQoS() {
        return qos;
    }

    public String getSubscribeTopicName() {
        return subscribeTopicName;
    }

    public void setSubscribeTopicName(String subscribeTopicName) {
        this.subscribeTopicName = subscribeTopicName;
    }

    public String getSubscribeTopicNames() {
        return subscribeTopicNames;
    }

    public void setSubscribeTopicNames(String subscribeTopicNames) {
        this.subscribeTopicNames = subscribeTopicNames;
    }

    public String getPublishTopicName() {
        return publishTopicName;
    }

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

    public void setMqttRetainPropertyName(String mqttRetainPropertyName) {
        this.mqttRetainPropertyName = mqttRetainPropertyName;
    }

    public String getMqttQosPropertyName() {
        return mqttQosPropertyName;
    }

    public void setMqttQosPropertyName(String mqttQosPropertyName) {
        this.mqttQosPropertyName = mqttQosPropertyName;
    }

    public int getConnectWaitInSeconds() {
        return connectWaitInSeconds;
    }

    public void setConnectWaitInSeconds(int connectWaitInSeconds) {
        this.connectWaitInSeconds = connectWaitInSeconds;
    }

    public int getDisconnectWaitInSeconds() {
        return disconnectWaitInSeconds;
    }

    public void setDisconnectWaitInSeconds(int disconnectWaitInSeconds) {
        this.disconnectWaitInSeconds = disconnectWaitInSeconds;
    }

    public int getSendWaitInSeconds() {
        return sendWaitInSeconds;
    }

    public void setSendWaitInSeconds(int sendWaitInSeconds) {
        this.sendWaitInSeconds = sendWaitInSeconds;
    }

    public boolean isByDefaultRetain() {
        return byDefaultRetain;
    }

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

    @Override
    public void setTracer(Tracer tracer) {
        super.setTracer(tracer);
    }

    @Override
    public void setClientId(String clientId) {
        super.setClientId(clientId);
    }

    @Override
    public void setClientId(UTF8Buffer clientId) {
        super.setClientId(clientId);
    }

    @Override
    public void setKeepAlive(short keepAlive) {
        super.setKeepAlive(keepAlive);
    }

    @Override
    public void setPassword(String password) {
        super.setPassword(password);
    }

    @Override
    public void setPassword(UTF8Buffer password) {
        super.setPassword(password);
    }

    @Override
    public void setUserName(String userName) {
        super.setUserName(userName);
    }

    @Override
    public void setUserName(UTF8Buffer userName) {
        super.setUserName(userName);
    }

    @Override
    public void setWillMessage(String willMessage) {
        super.setWillMessage(willMessage);
    }

    @Override
    public void setWillMessage(UTF8Buffer willMessage) {
        super.setWillMessage(willMessage);
    }

    @Override
    public void setWillQos(QoS willQos) {
        super.setWillQos(willQos);
    }

    @Override
    public void setVersion(String version) {
        super.setVersion(version);
    }

    @Override
    public String getVersion() {
        return super.getVersion();
    }

    @Override
    public void setWillRetain(boolean willRetain) {
        super.setWillRetain(willRetain);
    }

    @Override
    public void setWillTopic(String willTopic) {
        super.setWillTopic(willTopic);
    }

    @Override
    public void setWillTopic(UTF8Buffer willTopic) {
        super.setWillTopic(willTopic);
    }

    @Override
    public Executor getBlockingExecutor() {
        return super.getBlockingExecutor();
    }

    @Override
    public void setBlockingExecutor(Executor blockingExecutor) {
        super.setBlockingExecutor(blockingExecutor);
    }

    @Override
    public DispatchQueue getDispatchQueue() {
        return super.getDispatchQueue();
    }

    @Override
    public void setDispatchQueue(DispatchQueue dispatchQueue) {
        super.setDispatchQueue(dispatchQueue);
    }

    @Override
    public URI getLocalAddress() {
        return super.getLocalAddress();
    }

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

    @Override
    public void setMaxReadRate(int maxReadRate) {
        super.setMaxReadRate(maxReadRate);
    }

    @Override
    public int getMaxWriteRate() {
        return super.getMaxWriteRate();
    }

    @Override
    public void setMaxWriteRate(int maxWriteRate) {
        super.setMaxWriteRate(maxWriteRate);
    }

    @Override
    public int getReceiveBufferSize() {
        return super.getReceiveBufferSize();
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        super.setReceiveBufferSize(receiveBufferSize);
    }

    @Override
    public URI getHost() {
        return super.getHost();
    }

    @Override
    public void setHost(String host, int port) throws URISyntaxException {
        super.setHost(host, port);
    }

    @Override
    public void setHost(String host) throws URISyntaxException {
        super.setHost(host);
    }

    @Override
    public void setHost(URI host) {
        super.setHost(host);
    }

    @Override
    public int getSendBufferSize() {
        return super.getSendBufferSize();
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        super.setSendBufferSize(sendBufferSize);
    }

    @Override
    public SSLContext getSslContext() {
        return super.getSslContext();
    }

    @Override
    public void setSslContext(SSLContext sslContext) {
        super.setSslContext(sslContext);
    }

    @Override
    public int getTrafficClass() {
        return super.getTrafficClass();
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        super.setTrafficClass(trafficClass);
    }

    @Override
    public boolean isUseLocalHost() {
        return super.isUseLocalHost();
    }

    @Override
    public void setUseLocalHost(boolean useLocalHost) {
        super.setUseLocalHost(useLocalHost);
    }

    @Override
    public long getConnectAttemptsMax() {
        return super.getConnectAttemptsMax();
    }

    @Override
    public void setConnectAttemptsMax(long connectAttemptsMax) {
        super.setConnectAttemptsMax(connectAttemptsMax);
    }

    @Override
    public long getReconnectAttemptsMax() {
        return super.getReconnectAttemptsMax();
    }

    @Override
    public void setReconnectAttemptsMax(long reconnectAttemptsMax) {
        super.setReconnectAttemptsMax(reconnectAttemptsMax);
    }

    @Override
    public double getReconnectBackOffMultiplier() {
        return super.getReconnectBackOffMultiplier();
    }

    @Override
    public void setReconnectBackOffMultiplier(double reconnectBackOffMultiplier) {
        super.setReconnectBackOffMultiplier(reconnectBackOffMultiplier);
    }

    @Override
    public long getReconnectDelay() {
        return super.getReconnectDelay();
    }

    @Override
    public void setReconnectDelay(long reconnectDelay) {
        super.setReconnectDelay(reconnectDelay);
    }

    @Override
    public long getReconnectDelayMax() {
        return super.getReconnectDelayMax();
    }

    @Override
    public void setReconnectDelayMax(long reconnectDelayMax) {
        super.setReconnectDelayMax(reconnectDelayMax);
    }

    @Override
    public Tracer getTracer() {
        return super.getTracer();
    }
}



