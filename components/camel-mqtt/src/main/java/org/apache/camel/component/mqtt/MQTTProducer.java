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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.fusesource.mqtt.client.Callback;
import org.fusesource.mqtt.client.QoS;

public class MQTTProducer extends DefaultAsyncProducer implements Processor {

    private final MQTTEndpoint mqttEndpoint;

    public MQTTProducer(MQTTEndpoint mqttEndpoint) {
        super(mqttEndpoint);
        this.mqttEndpoint = mqttEndpoint;
    }
    
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (!mqttEndpoint.isConnected()) {
            try {
                ensureConnected();
            } catch (Exception e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        byte[] body = exchange.getIn().getBody(byte[].class);
        if (body != null) {
            MQTTConfiguration configuration = mqttEndpoint.getConfiguration();
            boolean retain = exchange.getProperty(configuration.getMqttRetainPropertyName(), configuration.isByDefaultRetain(), Boolean.class);

            QoS qoS = configuration.getQoS();
            Object qoSValue = exchange.getProperty(configuration.getMqttQosPropertyName());
            if (qoSValue != null) {
                qoS = MQTTConfiguration.getQoS(qoSValue.toString());
            }

            // where should we publish to
            String topicName = configuration.getPublishTopicName();
            // get the topic name by using the header of MQTT_PUBLISH_TOPIC
            Object topicValue = exchange.getIn().getHeader(MQTTConfiguration.MQTT_PUBLISH_TOPIC);
            if (topicValue != null) {
                topicName = topicValue.toString();
            }
            final String name = topicName;

            try {
                log.debug("Publishing to {}", name);
                mqttEndpoint.publish(name, body, qoS, retain, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        log.trace("onSuccess from {}", name);
                        callback.done(false);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        log.trace("onFailure from {}", name);
                        exchange.setException(throwable);
                        callback.done(false);
                    }
                });
            } catch (Exception e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }

            // we continue async, as the mqtt endpoint will invoke the callback when its done
            return false;
        } else {
            // no data to send so we are done
            log.trace("No data to publish");
            callback.done(true);
            return true;
        }
    }

    protected void doStart() throws Exception {
        if (!mqttEndpoint.getConfiguration().isLazySessionCreation()) {
            ensureConnected();
        }
        super.doStart();
    }

    protected synchronized void ensureConnected() throws Exception {
        if (!mqttEndpoint.isConnected()) {
            mqttEndpoint.connect();
        }
    }

}
