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
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.alibaba.mns.constants.MNSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendMessageTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("mnsClient")
    MNSClient mnsClient = mock(MNSClient.class);

    CloudQueue cloudQueue = mock(CloudQueue.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("alibaba-mns:" + testConfiguration.getProperty("queue")
                            + "?operation=sendMessage"
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accountEndpoint=" + testConfiguration.getProperty("accountEndpoint")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&mnsClient=#mnsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testSendMessage() throws Exception {
        Message response = new Message("response");
        response.setMessageId("message-id-123");
        response.setRequestId("request-id-456");
        response.setMessageBodyMD5("md5-value");

        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        when(cloudQueue.putMessage(any(Message.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:send", "hello mns");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertThat(exchange.getProperty(MNSHeaders.MESSAGE_ID)).isEqualTo("message-id-123");
        assertThat(exchange.getProperty(MNSProperties.REQUEST_ID)).isEqualTo("request-id-456");
        assertThat(exchange.getProperty(MNSHeaders.MESSAGE_BODY_MD5)).isEqualTo("md5-value");

        verify(cloudQueue).putMessage(any(Message.class));
    }
}
