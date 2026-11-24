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
package org.apache.camel.component.ignite;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ignite.messaging.IgniteMessagingComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * A route from one ignite-messaging topic to another: the consumer sets the topic header to the topic it received the
 * message on, and the producer must send to the topic of its endpoint, not back to that topic.
 */
class IgniteMessagingBridgeTest extends AbstractIgniteTest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final transient List<UUID> listeners = new CopyOnWriteArrayList<>();

    @Override
    protected String getScheme() {
        return "ignite-messaging";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteMessagingComponent.fromConfiguration(createConfiguration());
    }

    @Test
    void routeFromOneTopicToAnotherSendsToTheTopicOfTheProducer() throws Exception {
        String from = "from-" + resourceUid;
        String to = "to-" + resourceUid;
        List<Object> onFrom = listen(from);
        List<Object> onTo = listen(to);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ignite-messaging:" + from).routeId("bridge")
                        .to("mock:bridged")
                        .to("ignite-messaging:" + to);
            }
        });
        MockEndpoint bridged = getMockEndpoint("mock:bridged");
        bridged.expectedBodiesReceived("hello");
        bridged.setAssertPeriod(1000);

        ignite().message().send(from, "hello");

        await().atMost(Duration.ofSeconds(10)).until(() -> onTo.size() == 1);
        // the route got the message once: it was not sent back to the topic it came from
        bridged.assertIsSatisfied();
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> onFrom.size() == 1 && onTo.size() == 1);
    }

    @Test
    void topicHeaderSetByTheRouteIsUsed() throws Exception {
        String from = "from-" + resourceUid;
        String to = "to-" + resourceUid;
        String other = "other-" + resourceUid;
        List<Object> onTo = listen(to);
        List<Object> onOther = listen(other);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ignite-messaging:" + from).routeId("header")
                        .setHeader(IgniteConstants.IGNITE_MESSAGING_TOPIC, constant(other))
                        .to("ignite-messaging:" + to);
            }
        });

        ignite().message().send(from, "hello");

        await().atMost(Duration.ofSeconds(10)).until(() -> onOther.size() == 1);
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> onOther.size() == 1 && onTo.isEmpty());
    }

    private List<Object> listen(String topic) {
        List<Object> messages = new CopyOnWriteArrayList<>();
        listeners.add(ignite().message().remoteListen(topic, new IgniteBiPredicate<UUID, Object>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean apply(UUID nodeId, Object message) {
                messages.add(message);
                return true;
            }
        }));
        return messages;
    }

    @AfterEach
    void stopListeners() {
        for (UUID listener : listeners) {
            ignite().message().stopRemoteListen(listener);
        }
        listeners.clear();
    }
}
