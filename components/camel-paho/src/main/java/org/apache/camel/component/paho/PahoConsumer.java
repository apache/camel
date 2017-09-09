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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PahoConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PahoConsumer.class);

    public PahoConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String topic = getEndpoint().getTopic();
        getEndpoint().getClient().subscribe(topic, getEndpoint().getQos());
        getEndpoint().getClient().setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    try {
                        getEndpoint().getClient().subscribe(topic, getEndpoint().getQos());
                    } catch (MqttException e) {
                        LOG.error("MQTT resubscribe failed " + e.getMessage(), e);
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                LOG.debug("MQTT broker connection lost due " + cause.getMessage(), cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                LOG.debug("Message arrived on topic: {} -> {}", topic, message);
                Exchange exchange = getEndpoint().createExchange(message, topic);

                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        // noop
                    }
                });
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                LOG.debug("Delivery complete. Token: {}", token);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (getEndpoint().getClient().isConnected()) {
            String topic = getEndpoint().getTopic();
            getEndpoint().getClient().unsubscribe(topic);
        }
    }

    @Override
    public PahoEndpoint getEndpoint() {
        return (PahoEndpoint) super.getEndpoint();
    }

}