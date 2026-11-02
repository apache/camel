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
import com.aliyun.mns.model.Base64TopicMessage;
import com.aliyun.mns.model.Message;
import com.aliyun.mns.model.TopicMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.alibaba.mns.constants.MNSProperties;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * sendMessage and publishMessage set the message id and the MD5 of the body as headers, as the consumer does and as the
 * docs say, and also as exchange properties, as before.
 */
class MNSProducerResponseHeadersTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final MNSClient mnsClient = mock(MNSClient.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);
    private final CloudTopic cloudTopic = mock(CloudTopic.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        when(mnsClient.getTopicRef(testConfiguration.getProperty("topic"))).thenReturn(cloudTopic);
        context.getRegistry().bind("mnsClient", mnsClient);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to(uri(testConfiguration.getProperty("queue"), "sendMessage"));
                from("direct:publish")
                        .to(uri("topic:" + testConfiguration.getProperty("topic"), "publishMessage"));
            }
        };
    }

    @Test
    void sendMessageSetsTheMessageIdAndMd5AsHeaders() throws Exception {
        Message response = new Message();
        response.setMessageId("message-id-123");
        response.setRequestId("request-id-456");
        response.setMessageBodyMD5("md5-value");
        when(cloudQueue.putMessage(any(Message.class))).thenReturn(response);

        Exchange exchange = template.send("direct:send", e -> e.getMessage().setBody("hello mns"));

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(MNSHeaders.MESSAGE_ID)).isEqualTo("message-id-123");
        assertThat(exchange.getMessage().getHeader(MNSHeaders.MESSAGE_BODY_MD5)).isEqualTo("md5-value");
        // as before, for the routes that read them
        assertThat(exchange.getProperty(MNSHeaders.MESSAGE_ID)).isEqualTo("message-id-123");
        assertThat(exchange.getProperty(MNSHeaders.MESSAGE_BODY_MD5)).isEqualTo("md5-value");
        assertThat(exchange.getProperty(MNSProperties.REQUEST_ID)).isEqualTo("request-id-456");
        assertThat(exchange.getMessage().getBody()).isEqualTo("hello mns");
    }

    @Test
    void publishMessageSetsTheMessageIdAndMd5AsHeaders() throws Exception {
        TopicMessage response = new Base64TopicMessage();
        response.setMessageId("topic-message-id");
        response.setRequestId("topic-request-id");
        response.setMessageBodyMD5("topic-md5");
        when(cloudTopic.publishMessage(any(TopicMessage.class))).thenReturn(response);

        Exchange exchange = template.send("direct:publish", e -> e.getMessage().setBody("hello topic"));

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(MNSHeaders.MESSAGE_ID)).isEqualTo("topic-message-id");
        assertThat(exchange.getMessage().getHeader(MNSHeaders.MESSAGE_BODY_MD5)).isEqualTo("topic-md5");
        assertThat(exchange.getProperty(MNSHeaders.MESSAGE_ID)).isEqualTo("topic-message-id");
        assertThat(exchange.getProperty(MNSProperties.REQUEST_ID)).isEqualTo("topic-request-id");
    }

    @Test
    void sendMessageReplacesTheMessageIdOfAConsumedMessage() throws Exception {
        Message response = new Message();
        response.setMessageId("sent-id");
        response.setMessageBodyMD5("sent-md5");
        when(cloudQueue.putMessage(any(Message.class))).thenReturn(response);

        // such as a message consumed from another queue and sent on
        Exchange exchange = template.send("direct:send", e -> {
            e.getMessage().setBody("hello mns");
            e.getMessage().setHeader(MNSHeaders.MESSAGE_ID, "consumed-id");
            e.getMessage().setHeader(MNSHeaders.MESSAGE_BODY_MD5, "consumed-md5");
        });

        assertThat(exchange.getMessage().getHeader(MNSHeaders.MESSAGE_ID)).isEqualTo("sent-id");
        assertThat(exchange.getMessage().getHeader(MNSHeaders.MESSAGE_BODY_MD5)).isEqualTo("sent-md5");
    }

    private String uri(String path, String operation) {
        return "alibaba-mns:" + path
               + "?operation=" + operation
               + "&region=" + testConfiguration.getProperty("region")
               + "&accountEndpoint=" + testConfiguration.getProperty("accountEndpoint")
               + "&accessKey=" + testConfiguration.getProperty("accessKey")
               + "&secretKey=" + testConfiguration.getProperty("secretKey")
               + "&mnsClient=#mnsClient";
    }
}
