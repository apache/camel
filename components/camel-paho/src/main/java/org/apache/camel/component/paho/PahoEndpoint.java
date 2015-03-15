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
package org.apache.camel.component.paho;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import static java.lang.System.nanoTime;
import static org.apache.camel.component.paho.PahoPersistence.MEMORY;

@UriEndpoint(scheme = "paho", consumerClass = PahoConsumer.class, label = "messaging", syntax = "paho:topic")
public class PahoEndpoint extends DefaultEndpoint {

    // Configuration members

    @UriPath @Metadata(required = "true")
    private String topic;
    @UriParam
    private String clientId = "camel-" + nanoTime();
    @UriParam(defaultValue = "tcp://localhost:1883")
    private String brokerUrl = "tcp://localhost:1883";
    @UriParam(defaultValue = "2")
    private int qos = 2;
    @UriParam(defaultValue = "MEMORY")
    private PahoPersistence persistence = MEMORY;

    // Collaboration members
    @UriParam
    private MqttConnectOptions connectOptions;

    // Auto-configuration members

    private transient MqttClient client;

    public PahoEndpoint(String uri, Component component) {
        super(uri, component);
        if (topic == null) {
            topic = uri.substring(7);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client = new MqttClient(getBrokerUrl(), getClientId(), resolvePersistence());
        client.connect(resolveMqttConnectOptions());
    }

    @Override
    protected void doStop() throws Exception {
        if (getClient().isConnected()) {
            getClient().disconnect();
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PahoProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new PahoConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public PahoComponent getComponent() {
        return (PahoComponent) super.getComponent();
    }

    // Resolvers

    protected MqttClientPersistence resolvePersistence() {
        return persistence == MEMORY ? new MemoryPersistence() : new MqttDefaultFilePersistence();
    }

    protected MqttConnectOptions resolveMqttConnectOptions() {
        if (connectOptions != null) {
            return connectOptions;
        }
        return new MqttConnectOptions();
    }

    // Configuration getters & setters

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    // Auto-configuration getters & setters

    public PahoPersistence getPersistence() {
        return persistence;
    }

    public void setPersistence(PahoPersistence persistence) {
        this.persistence = persistence;
    }

    public MqttClient getClient() {
        return client;
    }

    public void setClient(MqttClient client) {
        this.client = client;
    }

    public MqttConnectOptions getConnectOptions() {
        return connectOptions;
    }

    public void setConnectOptions(MqttConnectOptions connOpts) {
        this.connectOptions = connOpts;
    }
}