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

import java.util.Optional;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;

public class HiveMQProducer extends DefaultAsyncProducer {

    private final HiveMQEndpoint endpoint;
    private HiveMQClientAdapter client;

    public HiveMQProducer(HiveMQEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client = endpoint.createClient();
        endpoint.connect(client);
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.stop();
        }
        client = null;
        super.doStop();
    }

    /**
     * Gives direct access to the underlying HiveMQ MQTT Client library client (e.g. {@code Mqtt5AsyncClient} or
     * {@code Mqtt3AsyncClient}, depending on the {@code mqttVersion} this producer is connected with) for use cases
     * this component does not cover. Empty before the producer has started, or if {@code clazz} does not match the
     * protocol version in use.
     */
    public <T> Optional<T> getClient(Class<T> clazz) {
        return client == null ? Optional.empty() : client.getClient(clazz);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String targetTopic = exchange.getIn().getHeader(HiveMQConstants.OVERRIDE_TOPIC, endpoint.getTopic(), String.class);
        MqttQos qos = exchange.getIn().getHeader(HiveMQConstants.MQTT_QOS, endpoint.getConfiguration().getQos(), MqttQos.class);
        boolean retained = exchange.getIn().getHeader(HiveMQConstants.MQTT_RETAINED, endpoint.getConfiguration().isRetained(),
                Boolean.class);

        byte[] payload = exchange.getIn().getBody(byte[].class);
        if (payload == null) {
            payload = new byte[0];
        }

        client.publish(targetTopic, payload, qos, retained)
                .whenComplete((publishResult, throwable) -> {
                    if (throwable != null) {
                        exchange.setException(throwable);
                    }
                    callback.done(false);
                });

        return false;
    }
}
