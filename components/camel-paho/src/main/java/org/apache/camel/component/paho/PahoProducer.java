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

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PahoProducer extends DefaultProducer {

    private volatile MqttClient client;
    private volatile String clientId;
    private volatile boolean stopClient;
    private volatile MqttConnectOptions connectOptions;

    public PahoProducer(PahoEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String topic = exchange.getIn().getHeader(PahoConstants.CAMEL_PAHO_OVERRIDE_TOPIC, getEndpoint().getTopic(), String.class);
        int qos = exchange.getIn().getHeader(PahoConstants.CAMEL_PAHO_MSG_QOS, getEndpoint().getConfiguration().getQos(), Integer.class);
        boolean retained = exchange.getIn().getHeader(PahoConstants.CAMEL_PAHO_MSG_RETAINED, getEndpoint().getConfiguration().isRetained(), Boolean.class);
        byte[] payload = exchange.getIn().getBody(byte[].class);

        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(retained);

        log.debug("Publishing to topic: {}, qos: {}, retrained: {}", topic, qos, retained);
        client.publish(topic, message);
    }

    @Override
    public PahoEndpoint getEndpoint() {
        return (PahoEndpoint)super.getEndpoint();
    }

    public MqttClient getClient() {
        return client;
    }

    public void setClient(MqttClient client) {
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        connectOptions = PahoEndpoint.createMqttConnectOptions(getEndpoint().getConfiguration());

        if (client == null) {
            clientId = getEndpoint().getConfiguration().getClientId();
            if (clientId == null) {
                clientId = "camel-" + MqttClient.generateClientId();
            }
            stopClient = true;
            client = new MqttClient(getEndpoint().getConfiguration().getBrokerUrl(),
                    clientId,
                    PahoEndpoint.createMqttClientPersistence(getEndpoint().getConfiguration()));
            log.debug("Connecting client: {} to broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
            client.connect(connectOptions);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (stopClient && client != null && client.isConnected()) {
            log.debug("Disconnecting client: {} from broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
            client.disconnect();
            client = null;
        }
    }

}
