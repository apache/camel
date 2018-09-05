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

import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for communicating with MQTT M2M message brokers using Eclipse Paho MQTT Client.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "paho", title = "Paho", consumerClass = PahoConsumer.class, label = "messaging,iot", syntax = "paho:topic")
public class PahoEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(PahoEndpoint.class);

    // Configuration members
    @UriPath
    @Metadata(required = "true")
    private String topic;
    @UriParam
    private String clientId = "camel-" + System.nanoTime();
    @UriParam(defaultValue = PahoConstants.DEFAULT_BROKER_URL)
    private String brokerUrl = PahoConstants.DEFAULT_BROKER_URL;
    @UriParam(defaultValue = "2")
    private int qos = PahoConstants.DEFAULT_QOS;
    @UriParam
    private boolean retained;
    @UriParam(defaultValue = "MEMORY")
    private PahoPersistence persistence = PahoPersistence.MEMORY;
    @UriParam(description = "Base directory used by file persistence. Will by default use current directory.")
    private String filePersistenceDirectory;
    @UriParam(defaultValue = "true")
    private boolean autoReconnect = true; 
    @UriParam @Metadata(secret = true)
    private String userName; 
    @UriParam @Metadata(secret = true)
    private String password; 
    

    // Collaboration members
    @UriParam
    private MqttConnectOptions connectOptions;

    // Auto-configuration members

    private transient MqttClient client;

    public PahoEndpoint(String uri, String topic, Component component) {
        super(uri, component);
        this.topic = topic;
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
        return (PahoComponent)super.getComponent();
    }

    // Resolvers
    protected MqttClientPersistence resolvePersistence() {
        if (persistence ==  PahoPersistence.MEMORY) {
            return new MemoryPersistence();
        } else {
            if (filePersistenceDirectory != null) {
                return new MqttDefaultFilePersistence(filePersistenceDirectory);
            } else {
                return new MqttDefaultFilePersistence();
            }
        }
    }

    protected MqttConnectOptions resolveMqttConnectOptions() {
        if (connectOptions != null) {
            return connectOptions;
        }
        Set<MqttConnectOptions> connectOptions = getCamelContext().getRegistry().findByType(MqttConnectOptions.class);
        if (connectOptions.size() == 1) {
            LOG.info("Single MqttConnectOptions instance found in the registry. It will be used by the endpoint.");
            return connectOptions.iterator().next();
        } else if (connectOptions.size() > 1) {
            LOG.warn("Found {} instances of the MqttConnectOptions in the registry. None of these will be used by the endpoint. "
                     + "Please use 'connectOptions' endpoint option to select one.", connectOptions.size());
        }
        
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(autoReconnect);
        
        if (ObjectHelper.isNotEmpty(userName) && ObjectHelper.isNotEmpty(password)) {
            options.setUserName(userName);
            options.setPassword(password.toCharArray());
        }
        return options;
    }

    public Exchange createExchange(MqttMessage mqttMessage, String topic) {
        Exchange exchange = createExchange();

        PahoMessage paho = new PahoMessage(exchange.getContext(), mqttMessage);
        paho.setBody(mqttMessage.getPayload());
        paho.setHeader(PahoConstants.MQTT_TOPIC, topic);

        exchange.setIn(paho);
        return exchange;
    }

    // Configuration getters & setters

    public String getClientId() {
        return clientId;
    }

    /**
     * MQTT client identifier.
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

    public String getTopic() {
        return topic;
    }

    /**
     * Name of the topic
     */
    public void setTopic(String topic) {
        this.topic = topic;
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
     * 
     * @param retained true/false
     */
    public void setRetained(boolean retained) {
        this.retained = retained;
    }

    // Auto-configuration getters & setters

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
     * Base directory used by the file persistence provider.
     */
    public void setFilePersistenceDirectory(String filePersistenceDirectory) {
        this.filePersistenceDirectory = filePersistenceDirectory;
    }

    public MqttClient getClient() {
        return client;
    }

    /**
     * To use the existing MqttClient instance as client.
     */
    public void setClient(MqttClient client) {
        this.client = client;
    }

    public MqttConnectOptions getConnectOptions() {
        return connectOptions;
    }

    /**
     * Client connection options
     */
    public void setConnectOptions(MqttConnectOptions connOpts) {
        this.connectOptions = connOpts;
    }

    public synchronized boolean isAutoReconnect() {
        return autoReconnect;
    }
    
    /**
     * Client will automatically attempt to reconnect to the server if the connection is lost 
     * @param autoReconnect
     */
    public synchronized void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Username to be used for authentication against the MQTT broker
     * @param userName
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to be used for authentication against the MQTT broker
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

}
