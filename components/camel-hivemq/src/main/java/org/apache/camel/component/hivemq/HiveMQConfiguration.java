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
package org.apache.camel.component.hivemq;

import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class HiveMQConfiguration implements Cloneable {

    @UriParam(defaultValue = HiveMQConstants.DEFAULT_HOST,
              description = "The host name or IP address of the MQTT broker.")
    private String host = HiveMQConstants.DEFAULT_HOST;

    /**
     * The MQTT protocol version to use when connecting to the broker.
     */
    @UriParam(defaultValue = "MQTT_5_0")
    private MqttVersion mqttVersion = MqttVersion.MQTT_5_0;

    @UriParam(defaultValue = "1883",
              description = "The TCP port of the MQTT broker. The port does not change when ssl is true: set it to the TLS"
                            + " port of the broker, usually 8883.")
    private int port = HiveMQConstants.DEFAULT_PORT;

    @UriParam(description = "The MQTT client identifier. If not set, the broker assigns one to each connection, but with"
                            + " MQTT 3.1.1 the broker then rejects a connection with cleanStart false. Each consumer and each"
                            + " producer opens its own connection, and the broker disconnects a client when another one"
                            + " connects with the same identifier, so a set identifier must be unique per endpoint.")
    private String clientId;

    @UriParam(defaultValue = "AT_LEAST_ONCE",
              description = "The Quality of Service (QoS) level that the producer publishes with, and the maximum QoS of the"
                            + " subscription of the consumer. The CamelHiveMQQos header replaces it for the producer. The"
                            + " consumer acknowledges a QoS 1 or 2 message when it receives it, before the route processes it.")
    private MqttQos qos = MqttQos.AT_LEAST_ONCE;

    @UriParam(defaultValue = "false",
              description = "Whether the producer publishes messages with the retain flag, so that the broker keeps the last"
                            + " message of the topic and sends it to new subscribers. The CamelHiveMQRetained header replaces"
                            + " it.")
    private boolean retained;

    /**
     * Whether to initiate a clean session upon connecting to the broker (called "clean session" in MQTT 3.1.1 and
     * "clean start" in MQTT 5). The two protocol versions behave differently when this is false: with MQTT 3.1.1 the
     * broker keeps the session (subscriptions and queued QoS 1/2 messages) indefinitely, while with MQTT 5 the session
     * still expires on disconnect, since this component does not set a session expiry interval.
     */
    @UriParam(defaultValue = "true")
    private boolean cleanStart = true;

    @UriParam(label = "security", description = "The user name for MQTT simple authentication with the broker.")
    @Metadata(label = "security")
    private String username;

    @UriParam(label = "security", secret = true,
              description = "The password for MQTT simple authentication with the broker. It is sent only when username is"
                            + " set.")
    @Metadata(label = "security", secret = true)
    private String password;

    @UriParam(defaultValue = "false", label = "security", security = "insecure:ssl", insecureValue = "false",
              description = "Whether to connect to the broker with TLS. The client uses the default trust store of the JVM"
                            + " and no client certificate. The port option does not change: set it to the TLS port of the"
                            + " broker, usually 8883.")
    private boolean ssl;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public MqttVersion getMqttVersion() {
        return mqttVersion;
    }

    public void setMqttVersion(MqttVersion mqttVersion) {
        this.mqttVersion = mqttVersion;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public MqttQos getQos() {
        return qos;
    }

    public void setQos(MqttQos qos) {
        this.qos = qos;
    }

    public boolean isRetained() {
        return retained;
    }

    public void setRetained(boolean retained) {
        this.retained = retained;
    }

    public boolean isCleanStart() {
        return cleanStart;
    }

    public void setCleanStart(boolean cleanStart) {
        this.cleanStart = cleanStart;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public HiveMQConfiguration copy() {
        try {
            return (HiveMQConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
