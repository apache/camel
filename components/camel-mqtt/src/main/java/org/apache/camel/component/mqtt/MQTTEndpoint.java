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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.Callback;
import org.fusesource.mqtt.client.CallbackConnection;
import org.fusesource.mqtt.client.Listener;
import org.fusesource.mqtt.client.Promise;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT endpoint
 */
public class MQTTEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(MQTTEndpoint.class);

    private CallbackConnection connection;
    private final MQTTConfiguration configuration;
    private final List<MQTTConsumer> consumers = new CopyOnWriteArrayList<MQTTConsumer>();

    public MQTTEndpoint(String uri, MQTTComponent component, MQTTConfiguration properties) {
        super(uri, component);
        this.configuration = properties;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        MQTTConsumer answer = new MQTTConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MQTTProducer(this);
    }

    public MQTTConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        connection = configuration.callbackConnection();

        connection.listener(new Listener() {
            public void onConnected() {
                LOG.info("MQTT Connection connected to {}", configuration.getHost());
            }

            public void onDisconnected() {
                LOG.debug("MQTT Connection disconnected from {}", configuration.getHost());
            }

            public void onPublish(UTF8Buffer topic, Buffer body, Runnable ack) {
                if (!consumers.isEmpty()) {
                    Exchange exchange = createExchange();
                    exchange.getIn().setBody(body.toByteArray());
                    exchange.setProperty(configuration.getMqttTopicPropertyName(), topic.toString());
                    for (MQTTConsumer consumer : consumers) {
                        consumer.processExchange(exchange);
                    }
                }
                if (ack != null) {
                    ack.run();
                }
            }

            public void onFailure(Throwable value) {
                connection.disconnect(new Callback<Void>() {
                    public void onSuccess(Void value) {
                    }

                    public void onFailure(Throwable e) {
                        LOG.debug("Failed to disconnect from " + configuration.getHost() + ". This exception is ignored.", e);
                    }
                });
            }
        });

        final Promise<Object> promise = new Promise<Object>();
        connection.connect(new Callback<Void>() {
            public void onSuccess(Void value) {
                String subscribeTopicName = configuration.getSubscribeTopicName();
                subscribeTopicName = subscribeTopicName != null ? subscribeTopicName.trim() : null;

                if (subscribeTopicName != null && !subscribeTopicName.isEmpty()) {
                    Topic[] topics = {new Topic(subscribeTopicName, configuration.getQoS())};
                    connection.subscribe(topics, new Callback<byte[]>() {
                        public void onSuccess(byte[] value) {
                            promise.onSuccess(value);
                        }

                        public void onFailure(Throwable value) {
                            promise.onFailure(value);
                            connection.disconnect(null);
                        }
                    });
                } else {
                    promise.onSuccess(value);
                }

            }

            public void onFailure(Throwable value) {
                promise.onFailure(value);
                connection.disconnect(null);
            }
        });
        promise.await(configuration.getConnectWaitInSeconds(), TimeUnit.SECONDS);
    }

    protected void doStop() throws Exception {
        if (connection != null) {
            final Promise<Void> promise = new Promise<Void>();
            connection.disconnect(new Callback<Void>() {
                public void onSuccess(Void value) {
                    promise.onSuccess(value);
                }

                public void onFailure(Throwable value) {
                    promise.onFailure(value);
                }
            });
            promise.await(configuration.getDisconnectWaitInSeconds(), TimeUnit.SECONDS);
        }
        super.doStop();
    }

    void publish(String topic, byte[] payload, QoS qoS, boolean retain, Callback<Void> callback) throws Exception {
        connection.publish(topic, payload, qoS, retain, callback);
    }

    void addConsumer(MQTTConsumer consumer) {
        consumers.add(consumer);
    }

    void removeConsumer(MQTTConsumer consumer) {
        consumers.remove(consumer);
    }

    public boolean isSingleton() {
        return true;
    }
}
