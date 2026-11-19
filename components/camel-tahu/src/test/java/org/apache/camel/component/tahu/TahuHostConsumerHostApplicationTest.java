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
package org.apache.camel.component.tahu;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Message;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * A tahu-host consumer created after another one of the same host id (the consumer of a route that is reloaded or
 * removed and added again, or of another CamelContext in the same JVM) gets the Sparkplug messages of its own host
 * application, with the servers of its own endpoint. The consumers are only initialized: no MQTT server is needed, the
 * messages are given to the handler of the host application as Tahu gives them.
 */
class TahuHostConsumerHostApplicationTest extends CamelTestSupport {

    private static final String URI = TahuConstants.HOST_APP_SCHEME + ":HostAppOfConsumerTest?clientId=client1&servers=";

    @Test
    void consumerCreatedAgainGetsTheMessages() throws Exception {
        List<String> first = new CopyOnWriteArrayList<>();
        List<String> second = new CopyOnWriteArrayList<>();

        TahuHostConsumer consumer = createConsumer(URI + "Mqtt:tcp://localhost:1883", first);
        // the route of the consumer is reloaded, or removed and added again: a new consumer is created (the old one is
        // stopped, which needs a connected host application, so it is left alone here)
        TahuHostConsumer again = createConsumer(URI + "Mqtt:tcp://localhost:1884", second);

        deliver(again, "Node1");

        assertThat(second, contains("G1/Node1"));
        assertThat(first, is(empty()));
        // the new host application connects with the servers of the new endpoint
        assertThat(again.getTahuHostApplication(), not(sameInstance(consumer.getTahuHostApplication())));
    }

    @Test
    void consumersOfTwoContextsGetTheirOwnMessages() throws Exception {
        List<String> first = new CopyOnWriteArrayList<>();
        List<String> second = new CopyOnWriteArrayList<>();

        TahuHostConsumer consumer = createConsumer(URI + "Mqtt:tcp://localhost:1883", first);
        try (CamelContext other = new DefaultCamelContext()) {
            other.start();
            TahuHostConsumer otherConsumer = (TahuHostConsumer) other.getEndpoint(URI + "Mqtt:tcp://localhost:1883")
                    .createConsumer(exchange -> second.add(descriptor(exchange)));
            ServiceHelper.initService(otherConsumer);

            deliver(consumer, "Node1");
            deliver(otherConsumer, "Node2");

            assertThat(first, contains("G1/Node1"));
            assertThat(second, contains("G1/Node2"));
        }
    }

    private TahuHostConsumer createConsumer(String uri, List<String> received) throws Exception {
        Processor processor = exchange -> received.add(descriptor(exchange));
        Consumer consumer = context.getEndpoint(uri).createConsumer(processor);
        // initialized only: starting would connect to the MQTT servers
        ServiceHelper.initService(consumer);
        return (TahuHostConsumer) consumer;
    }

    private static String descriptor(Exchange exchange) {
        return exchange.getMessage().getHeader(TahuConstants.EDGE_NODE_DESCRIPTOR, String.class);
    }

    /**
     * Gives an NDATA message to the handler of the host application of the consumer, as Tahu does for a message it
     * receives.
     */
    private static void deliver(TahuHostConsumer consumer, String edgeNode) {
        EdgeNodeDescriptor descriptor = new EdgeNodeDescriptor("G1", edgeNode);
        SparkplugBPayload payload = new SparkplugBPayload.SparkplugBPayloadBuilder().setSeq(0L).createPayload();
        Message message = new Message.MessageBuilder(new Topic("spBv1.0", descriptor, MessageType.NDATA), payload).build();
        consumer.getTahuHostApplication().getEventHandler().onMessage(new MqttServerName("Mqtt"), descriptor, message);
    }
}
