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
package org.apache.camel.component.paho.mqtt5;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.eclipse.paho.mqttv5.client.MqttClient;

/**
 * The paho-mqtt5 component whose consumers get their own client from a supplier instead of creating a Paho client (the
 * package-private {@code PahoMqtt5Consumer.createClient()}), for the DST world of the consumer (dst package). The
 * endpoint and the consumer are the real classes. Two differences from the real component: the world sets the options
 * on the configuration of the component and the endpoint uri has none (this component and its endpoint are not the
 * annotated classes that the build generates configurers for), and there is no client option (a client given to the
 * endpoint is not passed on).
 */
public final class PahoMqtt5DstComponent extends PahoMqtt5Component {

    private final Supplier<MqttClient> clients;

    public PahoMqtt5DstComponent(CamelContext context, Supplier<MqttClient> clients) {
        super(context);
        this.clients = clients;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // the options come from the configuration of the component (setConfiguration); the endpoint uri has none
        return new PahoMqtt5Endpoint(uri, remaining, this, getConfiguration().copy()) {
            @Override
            public Consumer createConsumer(Processor processor) throws Exception {
                PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(this, processor) {
                    @Override
                    MqttClient createClient() {
                        return clients.get();
                    }
                };
                configureConsumer(consumer);
                return consumer;
            }
        };
    }
}
