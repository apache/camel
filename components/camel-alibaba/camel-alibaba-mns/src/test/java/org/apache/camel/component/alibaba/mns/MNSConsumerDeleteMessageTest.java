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
package org.apache.camel.component.alibaba.mns;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With deleteAfterRead the consumer deletes the message it received once the exchange is done, with the receipt handle
 * it received the message with, whatever the route did with the CamelAlibabaMnsReceiptHandle header in the meantime.
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves. The routes are synchronous,
 * so the delete is done when {@code poll()} returns.
 */
class MNSConsumerDeleteMessageTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final MNSClient mnsClient = mock(MNSClient.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);
    private final Queue<Message> queue = new ConcurrentLinkedQueue<>();
    private final List<String> deletes = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        when(cloudQueue.popMessage()).thenAnswer(i -> queue.poll());
        doAnswer(i -> deletes.add(i.getArgument(0))).when(cloudQueue).deleteMessage(anyString());
        context.getRegistry().bind("mnsClient", mnsClient);

        Message message = new Message("hello");
        message.setMessageId("m-1");
        message.setReceiptHandle("handle-1");
        queue.add(message);
        return context;
    }

    @Test
    void messageIsDeletedWhenTheRouteRemovesTheHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        MNSConsumer consumer = startRoute("", route -> route.removeHeaders("*"));

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(deletes).containsExactly("handle-1");
    }

    @Test
    void messageIsDeletedWhenTheRouteReplacesTheMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("reply");
        // the new message has none of the headers of the received one
        MNSConsumer consumer = startRoute("", route -> route.process(exchange -> {
            DefaultMessage message = new DefaultMessage(exchange);
            message.setBody("reply");
            exchange.setMessage(message);
        }));

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(deletes).containsExactly("handle-1");
    }

    @Test
    void messageIsDeletedWithTheReceiptHandleItWasReceivedWith() throws Exception {
        // control: the route keeps the headers the consumer set
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        mock.expectedHeaderReceived(MNSHeaders.RECEIPT_HANDLE, "handle-1");
        MNSConsumer consumer = startRoute("", route -> {
        });

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(deletes).containsExactly("handle-1");
    }

    @Test
    void messageIsNotDeletedWithoutDeleteAfterRead() throws Exception {
        // control
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        MNSConsumer consumer = startRoute("&deleteAfterRead=false", route -> {
        });

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(deletes).isEmpty();
    }

    private MNSConsumer startRoute(String options, Consumer<RouteDefinition> steps) throws Exception {
        String uri = "alibaba-mns:" + testConfiguration.getProperty("queue")
                     + "?region=" + testConfiguration.getProperty("region")
                     + "&accountEndpoint="
                     + URLEncoder.encode(testConfiguration.getProperty("accountEndpoint"), StandardCharsets.UTF_8)
                     + "&accessKey=" + testConfiguration.getProperty("accessKey")
                     + "&secretKey=" + testConfiguration.getProperty("secretKey")
                     + "&mnsClient=#mnsClient&startScheduler=false" + options;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from(uri).routeId("mns");
                steps.accept(route);
                route.to("mock:result");
            }
        });
        return (MNSConsumer) context.getRoute("mns").getConsumer();
    }
}
