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
package org.apache.camel.component.vertx.mqtt;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;

/**
 * Publishes the body of a message. The exchange completes when the broker acknowledged a message of quality of service
 * 1 or 2, and when the message is written for quality of service 0.
 */
public class VertxMqttProducer extends DefaultAsyncProducer {

    private VertxMqttConnection connection;

    public VertxMqttProducer(VertxMqttEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public VertxMqttEndpoint getEndpoint() {
        return (VertxMqttEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        connection = getEndpoint().acquireConnection();
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            connection = null;
            getEndpoint().releaseConnection();
        }
        super.doStop();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            final VertxMqttConfiguration configuration = getEndpoint().getConfiguration();
            final Message message = exchange.getMessage();
            final String topic = message.getHeader(VertxMqttConstants.OVERRIDE_TOPIC, getEndpoint().getTopic(), String.class);
            checkTopic(topic);
            final int qos = message.getHeader(VertxMqttConstants.QOS, configuration.getQos(), Integer.class);
            if (qos < 0 || qos > 2) {
                throw new IllegalArgumentException("The quality of service must be 0, 1 or 2, not %s".formatted(qos));
            }
            final boolean retain = message.getHeader(VertxMqttConstants.RETAINED, configuration.isRetained(), Boolean.class);
            final byte[] body = message.getBody(byte[].class);
            final MqttProperties properties = configuration.getMqttVersion() == VertxMqttVersion.MQTT_5_0
                    ? VertxMqttProperties.fromHeaders(message) : null;
            connection.publish(topic, Buffer.buffer(body == null ? new byte[0] : body), MqttQoS.valueOf(qos), retain,
                    properties).onComplete(done -> {
                        if (done.failed()) {
                            exchange.setException(done.cause());
                        }
                        callback.done(false);
                    });
            return false;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    /**
     * A topic to publish on names one topic, wildcards are for subscriptions. The Vert.x client does not check it.
     */
    static void checkTopic(String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("A message needs a topic to publish on");
        }
        if (topic.indexOf('+') >= 0 || topic.indexOf('#') >= 0) {
            throw new IllegalArgumentException(
                    "The topic to publish on must not hold the wildcards + or #: %s".formatted(topic));
        }
    }
}
