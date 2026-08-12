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

import com.aliyun.mns.client.CloudTopic;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Base64TopicMessage;
import com.aliyun.mns.model.TopicMessage;
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

class PublishMessageTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("mnsClient")
    MNSClient mnsClient = mock(MNSClient.class);

    CloudTopic cloudTopic = mock(CloudTopic.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publish")
                        .to("alibaba-mns:topic:" + testConfiguration.getProperty("topic")
                            + "?operation=publishMessage"
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
    void testPublishMessage() throws Exception {
        TopicMessage response = new Base64TopicMessage();
        response.setMessageId("topic-message-id");
        response.setRequestId("topic-request-id");

        when(mnsClient.getTopicRef(testConfiguration.getProperty("topic"))).thenReturn(cloudTopic);
        when(cloudTopic.publishMessage(any(TopicMessage.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:publish", "hello topic");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertThat(exchange.getProperty(MNSHeaders.MESSAGE_ID)).isEqualTo("topic-message-id");
        assertThat(exchange.getProperty(MNSProperties.REQUEST_ID)).isEqualTo("topic-request-id");

        verify(cloudTopic).publishMessage(any(TopicMessage.class));
    }
}
