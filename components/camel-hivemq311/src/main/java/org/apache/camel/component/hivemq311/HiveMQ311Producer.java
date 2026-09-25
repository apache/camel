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
package org.apache.camel.component.hivemq311;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;

public class HiveMQ311Producer extends DefaultAsyncProducer {

    private final HiveMQ311Endpoint endpoint;
    private Mqtt3AsyncClient client;

    public HiveMQ311Producer(HiveMQ311Endpoint endpoint) {
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
        endpoint.stopClient(client);
        client = null;
        super.doStop();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String targetTopic = exchange.getIn().getHeader(HiveMQ311Constants.OVERRIDE_TOPIC, endpoint.getTopic(), String.class);
        MqttQos qos = exchange.getIn().getHeader(HiveMQ311Constants.MQTT_QOS, endpoint.getConfiguration().getQos(),
                MqttQos.class);
        boolean retained = exchange.getIn().getHeader(HiveMQ311Constants.MQTT_RETAINED,
                endpoint.getConfiguration().isRetained(), Boolean.class);

        byte[] payload = exchange.getIn().getBody(byte[].class);
        if (payload == null) {
            payload = new byte[0];
        }

        client.publish(Mqtt3Publish.builder()
                .topic(targetTopic)
                .qos(qos)
                .retain(retained)
                .payload(payload)
                .build())
                .whenComplete((publishResult, throwable) -> {
                    if (throwable != null) {
                        exchange.setException(throwable);
                    }
                    callback.done(false);
                });

        return false;
    }
}
