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
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
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
    @UriParam(defaultValue = "camel/mqtt/test")
    private String publishTopicName = "camel/mqtt/test";
    @UriParam(defaultValue = "10")
    private int connectWaitInSeconds = 10;
    @UriParam(defaultValue = "5")
    private int disconnectWaitInSeconds = 5;
    @UriParam(defaultValue = "5")
    private int sendWaitInSeconds = 5;
    @UriParam(defaultValue = "false")
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

}



