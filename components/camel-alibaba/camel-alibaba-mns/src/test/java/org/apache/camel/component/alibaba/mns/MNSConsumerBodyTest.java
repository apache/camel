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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.alibaba.mns.MNSProducerBodyTest.BINARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The consumer gives the body of a message as text when it is UTF-8 text, and as bytes otherwise, so that the bytes the
 * producer sends, such as those of a binary file, can be read back. The fake queue holds the messages the producer
 * puts, which the consumer pops.
 */
class MNSConsumerBodyTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final MNSClient mnsClient = mock(MNSClient.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);
    private final Queue<Message> queue = new ConcurrentLinkedQueue<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        when(cloudQueue.putMessage(any(Message.class))).thenAnswer(i -> {
            queue.add(i.getArgument(0));
            return new Message();
        });
        when(cloudQueue.popMessage()).thenAnswer(i -> queue.poll());
        context.getRegistry().bind("mnsClient", mnsClient);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String options = "?region=" + testConfiguration.getProperty("region")
                         + "&accountEndpoint="
                         + URLEncoder.encode(testConfiguration.getProperty("accountEndpoint"), StandardCharsets.UTF_8)
                         + "&accessKey=" + testConfiguration.getProperty("accessKey")
                         + "&secretKey=" + testConfiguration.getProperty("secretKey")
                         + "&mnsClient=#mnsClient";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("alibaba-mns:" + testConfiguration.getProperty("queue") + options);

                from("alibaba-mns:" + testConfiguration.getProperty("queue") + options + "&initialDelay=0&delay=10")
                        .to("mock:result");
            }
        };
    }

    @Test
    void binaryMessageIsGivenAsBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        queue.add(new Message(BINARY));

        mock.assertIsSatisfied();
        assertThat(mock.getExchanges().get(0).getMessage().getBody()).isInstanceOf(byte[].class).isEqualTo(BINARY);
    }

    @Test
    void textMessageIsGivenAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        queue.add(new Message("hello mns é"));

        mock.assertIsSatisfied();
        assertThat(mock.getExchanges().get(0).getMessage().getBody()).isEqualTo("hello mns é");
    }

    @Test
    void bytesSentByTheProducerAreReadBackUnchanged() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBody("direct:send", BINARY);
        template.sendBody("direct:send", "hello mns é");

        mock.assertIsSatisfied();
        assertThat(mock.getExchanges().get(0).getMessage().getBody()).isEqualTo(BINARY);
        assertThat(mock.getExchanges().get(1).getMessage().getBody()).isEqualTo("hello mns é");
    }
}
