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

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class HiveMQConfiguration implements Cloneable {

    /**
     * Hostname or IP address of the HiveMQ MQTT broker.
     */
    @UriParam(defaultValue = HiveMQConstants.DEFAULT_HOST)
    private String host = HiveMQConstants.DEFAULT_HOST;

    /**
     * Port number of the HiveMQ MQTT broker.
     */
    @UriParam(defaultValue = "1883")
    private int port = HiveMQConstants.DEFAULT_PORT;

    /**
     * Client identifier used when connecting to the HiveMQ broker.
     */
    @UriParam
    private String clientId;

    /**
     * Default Quality of Service (QoS) level to use for messages.
     */
    @UriParam(defaultValue = "AT_LEAST_ONCE")
    private MqttQos qos = MqttQos.AT_LEAST_ONCE;

    /**
     * Whether published messages should be retained by the MQTT broker.
     */
    @UriParam(defaultValue = "false")
    private boolean retained;

    /**
     * Whether to initiate a clean start (MQTT 5) upon connecting to the broker.
     */
    @UriParam(defaultValue = "true")
    private boolean cleanStart = true;

    /**
     * Username for authentication with the HiveMQ broker.
     */
    @UriParam(label = "security")
    @Metadata(label = "security")
    private String username;

    /**
     * Password for authentication with the HiveMQ broker.
     */
    @UriParam(label = "security", secret = true)
    @Metadata(label = "security", secret = true)
    private String password;

    /**
     * Whether to enable SSL/TLS encryption for the broker connection.
     */
    @UriParam(defaultValue = "false")
    private boolean ssl;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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
