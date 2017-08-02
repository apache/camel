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
package org.apache.camel.component.ignite;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.ignite.messaging.IgniteMessagingComponent;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.junit.After;
import org.junit.Test;

import static com.google.common.truth.Truth.assert_;
import static org.awaitility.Awaitility.await;

public class IgniteMessagingTest extends AbstractIgniteTest implements Serializable {

    private static final long serialVersionUID = 3967738538216977749L;

    private static final String TOPIC1 = "TOPIC1";
    private static final String TOPIC2 = "TOPIC2";
    private UUID uuid;

    @Override
    protected String getScheme() {
        return "ignite-messaging";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteMessagingComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testProducerSendMessage() {
        List<Object> messages = Lists.newArrayList();
        setupMessageListener(TOPIC1, messages);

        template.requestBody("ignite-messaging:TOPIC1", 1);

        await().atMost(5, TimeUnit.SECONDS).until(() -> messages.size() == 1);
        assert_().that(messages.get(0)).isEqualTo(1);
    }

    @Test
    public void testProducerSendMessageTopicInHeader() throws Exception {
        List<Object> messages1 = Lists.newArrayList();
        setupMessageListener(TOPIC1, messages1);

        List<Object> messages2 = Lists.newArrayList();
        setupMessageListener(TOPIC2, messages2);

        template.requestBodyAndHeader("ignite-messaging:TOPIC1", 1, IgniteConstants.IGNITE_MESSAGING_TOPIC, "TOPIC2");

        Thread.sleep(1000);
        assert_().that(messages1.size()).isEqualTo(0);
        assert_().that(messages2.size()).isEqualTo(1);
    }

    @Test
    public void testProducerSendManyMessages() {
        List<Object> messages = Lists.newArrayList();
        setupMessageListener(TOPIC1, messages);

        Set<Integer> request = ContiguousSet.create(Range.closedOpen(0, 100), DiscreteDomain.integers());
        template.requestBody("ignite-messaging:TOPIC1", request);

        await().atMost(5, TimeUnit.SECONDS).until(() -> messages.size() == 100);
        assert_().that(messages).containsAllIn(request);
    }

    @Test
    public void testProducerSendManyMessagesOrdered() {
        List<Object> messages = Lists.newArrayList();
        setupMessageListener(TOPIC1, messages);

        ContiguousSet<Integer> set = ContiguousSet.create(Range.closedOpen(0, 100), DiscreteDomain.integers());
        for (int i : set) {
            template.requestBody("ignite-messaging:TOPIC1?sendMode=ORDERED&timeout=1000", i);
        }

        await().atMost(5, TimeUnit.SECONDS).until(() -> messages.size() == 100);
        assert_().that(messages).containsAllIn(set);
    }

    @Test
    public void testProducerSendCollectionAsObject() {
        List<Object> messages = Lists.newArrayList();
        setupMessageListener(TOPIC1, messages);

        Set<Integer> request = ContiguousSet.create(Range.closedOpen(0, 100), DiscreteDomain.integers());
        template.requestBody("ignite-messaging:TOPIC1?treatCollectionsAsCacheObjects=true", request);

        await().atMost(5, TimeUnit.SECONDS).until(() -> messages.size() == 1);
        assert_().that(messages.get(0)).isEqualTo(request);
    }

    @Test
    public void testConsumerManyMessages() throws Exception {
        List<Object> messages = Lists.newArrayList();
        Consumer consumer = context.getEndpoint("ignite-messaging:TOPIC1").createConsumer(storeBodyInListProcessor(messages));
        consumer.start();

        Set<Integer> messagesToSend = ContiguousSet.create(Range.closedOpen(0, 100), DiscreteDomain.integers());
        ignite().message().send(TOPIC1, messagesToSend);

        await().atMost(5, TimeUnit.SECONDS).until(() -> messages.size() == 100);

        consumer.stop();
    }

    private void setupMessageListener(String topic, final List<Object> messages) {
        uuid = ignite().message().remoteListen(topic, new IgniteBiPredicate<UUID, Object>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean apply(UUID uuid, Object message) {
                messages.add(message);
                return true;
            }
        });
    }

    @After
    public void stopMessageListener() {
        if (uuid == null) {
            return;
        }

        ignite().message().stopRemoteListen(uuid);
        uuid = null;
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    private Processor storeBodyInListProcessor(final List<Object> list) {
        return new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                list.add(exchange.getIn().getBody());
            }
        };
    }

}
