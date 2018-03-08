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
import java.util.concurrent.TimeoutException;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.mqtt.client.Callback;
import org.fusesource.mqtt.client.CallbackConnection;
import org.fusesource.mqtt.client.Listener;
import org.fusesource.mqtt.client.Promise;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.fusesource.mqtt.client.Tracer;
import org.fusesource.mqtt.codec.CONNACK;
import org.fusesource.mqtt.codec.CONNECT;
import org.fusesource.mqtt.codec.DISCONNECT;
import org.fusesource.mqtt.codec.MQTTFrame;
import org.fusesource.mqtt.codec.PINGREQ;
import org.fusesource.mqtt.codec.PINGRESP;
import org.fusesource.mqtt.codec.PUBACK;
import org.fusesource.mqtt.codec.PUBCOMP;
import org.fusesource.mqtt.codec.PUBLISH;
import org.fusesource.mqtt.codec.PUBREC;
import org.fusesource.mqtt.codec.PUBREL;
import org.fusesource.mqtt.codec.SUBACK;
import org.fusesource.mqtt.codec.SUBSCRIBE;
import org.fusesource.mqtt.codec.UNSUBSCRIBE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for communicating with MQTT M2M message brokers using FuseSource MQTT Client.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "mqtt", title = "MQTT", syntax = "mqtt:name", consumerClass = MQTTConsumer.class, label = "messaging,iot")
public class MQTTEndpoint extends DefaultEndpoint implements AsyncEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(MQTTEndpoint.class);

    private static final int PUBLISH_MAX_RECONNECT_ATTEMPTS = 3;

    private CallbackConnection connection;
    private volatile boolean connected;
    private final List<MQTTConsumer> consumers = new CopyOnWriteArrayList<MQTTConsumer>();

    @UriPath @Metadata(required = "true")
    private String name;

    @UriParam
    private final MQTTConfiguration configuration;

    public MQTTEndpoint(final String uri, MQTTComponent component, MQTTConfiguration properties) {
        super(uri, component);
        this.configuration = properties;
        if (LOG.isTraceEnabled()) {
            configuration.setTracer(new Tracer() {
                @Override
                public void debug(String message, Object...args) {
                    LOG.trace("tracer.debug() " + this + ": uri=" + uri + ", message=" + String.format(message, args));
                }

                @Override
                public void onSend(MQTTFrame frame) {
                    String decoded = null;
                    try {
                        switch (frame.messageType()) {
                        case PINGREQ.TYPE:
                            decoded = new PINGREQ().decode(frame).toString();
                            break;
                        case PINGRESP.TYPE:
                            decoded = new PINGRESP().decode(frame).toString();
                            break;
                        case CONNECT.TYPE:
                            decoded = new CONNECT().decode(frame).toString();
                            break;
                        case DISCONNECT.TYPE:
                            decoded = new DISCONNECT().decode(frame).toString();
                            break;
                        case SUBSCRIBE.TYPE:
                            decoded = new SUBSCRIBE().decode(frame).toString();
                            break;
                        case UNSUBSCRIBE.TYPE:
                            decoded = new UNSUBSCRIBE().decode(frame).toString();
                            break;
                        case PUBLISH.TYPE:
                            decoded = new PUBLISH().decode(frame).toString();
                            break;
                        case PUBACK.TYPE:
                            decoded = new PUBACK().decode(frame).toString();
                            break;
                        case PUBREC.TYPE:
                            decoded = new PUBREC().decode(frame).toString();
                            break;
                        case PUBREL.TYPE:
                            decoded = new PUBREL().decode(frame).toString();
                            break;
                        case PUBCOMP.TYPE:
                            decoded = new PUBCOMP().decode(frame).toString();
                            break;
                        case CONNACK.TYPE:
                            decoded = new CONNACK().decode(frame).toString();
                            break;
                        case SUBACK.TYPE:
                            decoded = new SUBACK().decode(frame).toString();
                            break;
                        default:
                            decoded = frame.toString();
                        }
                    } catch (Throwable e) {
                        decoded = frame.toString();
                    }
                    LOG.trace("tracer.onSend() " + this + ":  uri=" + uri + ", frame=" + decoded);
                }

                @Override
                public void onReceive(MQTTFrame frame) {
                    String decoded = null;
                    try {
                        switch (frame.messageType()) {
                        case PINGREQ.TYPE:
                            decoded = new PINGREQ().decode(frame).toString();
                            break;
                        case PINGRESP.TYPE:
                            decoded = new PINGRESP().decode(frame).toString();
                            break;
                        case CONNECT.TYPE:
                            decoded = new CONNECT().decode(frame).toString();
                            break;
                        case DISCONNECT.TYPE:
                            decoded = new DISCONNECT().decode(frame).toString();
                            break;
                        case SUBSCRIBE.TYPE:
                            decoded = new SUBSCRIBE().decode(frame).toString();
                            break;
                        case UNSUBSCRIBE.TYPE:
                            decoded = new UNSUBSCRIBE().decode(frame).toString();
                            break;
                        case PUBLISH.TYPE:
                            decoded = new PUBLISH().decode(frame).toString();
                            break;
                        case PUBACK.TYPE:
                            decoded = new PUBACK().decode(frame).toString();
                            break;
                        case PUBREC.TYPE:
                            decoded = new PUBREC().decode(frame).toString();
                            break;
                        case PUBREL.TYPE:
                            decoded = new PUBREL().decode(frame).toString();
                            break;
                        case PUBCOMP.TYPE:
                            decoded = new PUBCOMP().decode(frame).toString();
                            break;
                        case CONNACK.TYPE:
                            decoded = new CONNACK().decode(frame).toString();
                            break;
                        case SUBACK.TYPE:
                            decoded = new SUBACK().decode(frame).toString();
                            break;
                        default:
                            decoded = frame.toString();
                        }
                    } catch (Throwable e) {
                        decoded = frame.toString();
                    }
                    LOG.trace("tracer.onReceive() " + this + ":  uri=" + uri + ", frame=" + decoded);
                }
            });
        }
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

    public String getName() {
        return name;
    }

    /**
     * A logical name to use which is not the topic name.
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        createConnection();
    }

    protected void createConnection() {
        if (connection != null) {
            // In connect(), in the connection.connect() callback, onFailure() doesn't seem to ever be called, so forcing the disconnect here.
            // Without this, the fusesource MQTT client seems to be holding the old connection object, and connection contention can ensue.
            connection.disconnect(null);
        }

        connection = configuration.callbackConnection();

        connection.listener(new Listener() {
            public void onConnected() {
                connected = true;
                LOG.info("MQTT Connection connected to {}", configuration.getHost());
            }

            public void onDisconnected() {
                // no connected = false required here because the MQTT client should trigger its own reconnect;
                // setting connected = false would make the publish() method to launch a new connection while the original
                // one is still reconnecting, likely leading to duplicate messages as observed in CAMEL-9092;
                // if retries are exhausted and it desists, we should get a callback on onFailure, and then we can set
                // connected = false safely
                LOG.debug("MQTT Connection disconnected from {}", configuration.getHost());
            }

            public void onPublish(UTF8Buffer topic, Buffer body, Runnable ack) {
                if (!consumers.isEmpty()) {
                    Exchange exchange = createExchange();
                    exchange.getIn().setBody(body.toByteArray());
                    exchange.getIn().setHeader(MQTTConfiguration.MQTT_SUBSCRIBE_TOPIC, topic.toString());
                    for (MQTTConsumer consumer : consumers) {
                        consumer.processExchange(exchange);
                    }
                }
                if (ack != null) {
                    ack.run();
                }
            }

            public void onFailure(Throwable value) {
                // mark this connection as disconnected so we force re-connect
                connected = false;
                LOG.warn("Connection to " + configuration.getHost() + " failure due " + value.getMessage() + ". Forcing a disconnect to re-connect on next attempt.");
                connection.disconnect(new Callback<Void>() {
                    public void onSuccess(Void value) {
                    }

                    public void onFailure(Throwable e) {
                        LOG.debug("Failed to disconnect from " + configuration.getHost() + ". This exception is ignored.", e);
                    }
                });
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (connection != null && connected) {
            final Promise<Void> promise = new Promise<>();
            connection.getDispatchQueue().execute(new Task() {
                @Override
                public void run() {
                    connection.disconnect(new Callback<Void>() {
                        public void onSuccess(Void value) {
                            connected = false;
                            promise.onSuccess(value);
                        }
                        public void onFailure(Throwable value) {
                            promise.onFailure(value);
                        }
                    });
                }
            });

            promise.await(configuration.getDisconnectWaitInSeconds(), TimeUnit.SECONDS);
        }
    }

    void connect() throws Exception {
        final Promise<Object> promise = new Promise<Object>();
        connection.connect(new Callback<Void>() {
            public void onSuccess(Void value) {
                LOG.debug("Connected to {}", configuration.getHost());

                Topic[] topics = createSubscribeTopics();
                if (topics != null && topics.length > 0) {
                    connection.subscribe(topics, new Callback<byte[]>() {
                        public void onSuccess(byte[] value) {
                            promise.onSuccess(value);
                            connected = true;
                        }

                        public void onFailure(Throwable value) {
                            LOG.debug("Failed to subscribe", value);
                            promise.onFailure(value);
                            connection.disconnect(null);
                            connected = false;
                        }
                    });
                } else {
                    promise.onSuccess(value);
                    connected = true;
                }

            }

            public void onFailure(Throwable value) {  // this doesn't appear to ever be called
                LOG.warn("Failed to connect to " + configuration.getHost() + " due " + value.getMessage());
                promise.onFailure(value);
                connection.disconnect(null);
                connected = false;
            }
        });
        LOG.info("Connecting to {} using {} seconds timeout", configuration.getHost(), configuration.getConnectWaitInSeconds());
        promise.await(configuration.getConnectWaitInSeconds(), TimeUnit.SECONDS);
    }

    Topic[] createSubscribeTopics() {
        String subscribeTopicList = configuration.getSubscribeTopicNames();
        if (subscribeTopicList != null && !subscribeTopicList.isEmpty()) {
            String[] topicNames = subscribeTopicList.split(",");
            Topic[] topics = new Topic[topicNames.length];
            for (int i = 0; i < topicNames.length; i++) {
                topics[i] = new Topic(topicNames[i].trim(), configuration.getQoS());
            }
            return topics;
        } else { // fall back on singular topic name
            String subscribeTopicName = configuration.getSubscribeTopicName();
            subscribeTopicName = subscribeTopicName != null ? subscribeTopicName.trim() : null;
            if (subscribeTopicName != null && !subscribeTopicName.isEmpty()) {
                Topic[] topics = {new Topic(subscribeTopicName, configuration.getQoS())};
                return topics;
            }
        }
        LOG.warn("No topic subscriptions were specified in configuration");
        return null;
    }

    boolean isConnected() {
        return connected;
    }
 
    void publish(final String topic, final byte[] payload, final QoS qoS, final boolean retain, final Callback<Void> callback) throws Exception {
        // if not connected then create a new connection to re-connect
        boolean done = isConnected();
        int attempt = 0;
        TimeoutException timeout = null;
        while (!done && attempt <= PUBLISH_MAX_RECONNECT_ATTEMPTS) {
            attempt++;
            try {
                LOG.warn("#{} attempt to re-create connection to {} before publishing", attempt, configuration.getHost());
                createConnection();
                connect();
            } catch (TimeoutException e) {
                timeout = e;
                LOG.debug("Timed out after {} seconds after {} attempt to re-create connection to {}",
                        new Object[]{configuration.getConnectWaitInSeconds(), attempt, configuration.getHost()});
            } catch (Throwable e) {
                // other kind of error then exit asap
                callback.onFailure(e);
                return;
            }

            done = isConnected();
        }

        if (attempt > 3 && !isConnected()) {
            LOG.warn("Cannot re-connect to {} after {} attempts", configuration.getHost(), attempt);
            callback.onFailure(timeout);
            return;
        }

        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                LOG.debug("Publishing to {}", configuration.getHost());
                connection.publish(topic, payload, qoS, retain, callback);
            }
        });
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
