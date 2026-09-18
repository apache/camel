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
import java.util.concurrent.TimeUnit;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiveMessageConsumerTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("mnsClient")
    MNSClient mnsClient = mock(MNSClient.class);

    CloudQueue cloudQueue = mock(CloudQueue.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        String accountEndpoint = URLEncoder.encode(testConfiguration.getProperty("accountEndpoint"),
                StandardCharsets.UTF_8);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-mns:" + testConfiguration.getProperty("queue")
                     + "?region=" + testConfiguration.getProperty("region")
                     + "&accountEndpoint=" + accountEndpoint
                     + "&accessKey=" + testConfiguration.getProperty("accessKey")
                     + "&secretKey=" + testConfiguration.getProperty("secretKey")
                     + "&deleteAfterRead=true"
                     + "&initialDelay=100"
                     + "&delay=200"
                     + "&useFixedDelay=true"
                     + "&mnsClient=#mnsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testReceiveMessageAndDeleteAfterRead() throws Exception {
        Message message = new Message("received body");
        message.setMessageId("received-id");
        message.setReceiptHandle("receipt-handle-123");
        message.setMessageBodyMD5("received-md5");

        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        when(cloudQueue.popMessage()).thenReturn(message).thenReturn(null);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MNSHeaders.MESSAGE_ID, "received-id");
        mock.expectedHeaderReceived(MNSHeaders.RECEIPT_HANDLE, "receipt-handle-123");
        mock.expectedBodiesReceived("received body");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(cloudQueue).deleteMessage(eq("receipt-handle-123")));
    }
}
