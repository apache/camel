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

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.CloudTopic;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import com.aliyun.mns.model.TopicMessage;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MNSProducerWithoutKeysTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("mnsClient")
    MNSClient mnsClient = mock(MNSClient.class);

    CloudQueue cloudQueue = mock(CloudQueue.class);

    CloudTopic cloudTopic = mock(CloudTopic.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("alibaba-mns:" + testConfiguration.getProperty("queue") + "?mnsClient=#mnsClient");
                from("direct:publish")
                        .to("alibaba-mns:topic:" + testConfiguration.getProperty("topic") + "?mnsClient=#mnsClient");
                from("direct:delete")
                        .to("alibaba-mns:" + testConfiguration.getProperty("queue")
                            + "?operation=deleteMessage&mnsClient=#mnsClient");
            }
        };
    }

    @Test
    void testSendMessage() {
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);

        Exchange exchange = template.send("direct:send", e -> e.getMessage().setBody("hello mns"));

        assertThat(exchange.getException()).isNull();
        verify(cloudQueue).putMessage(any(Message.class));
    }

    @Test
    void testPublishMessage() {
        when(mnsClient.getTopicRef(testConfiguration.getProperty("topic"))).thenReturn(cloudTopic);

        Exchange exchange = template.send("direct:publish", e -> e.getMessage().setBody("hello topic"));

        assertThat(exchange.getException()).isNull();
        verify(cloudTopic).publishMessage(any(TopicMessage.class));
    }

    @Test
    void testDeleteMessage() throws Exception {
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);

        Exchange exchange = template.send("direct:delete",
                e -> e.getMessage().setHeader(MNSHeaders.RECEIPT_HANDLE, "receipt-handle-123"));

        assertThat(exchange.getException()).isNull();
        verify(cloudQueue).deleteMessage("receipt-handle-123");
    }
}
