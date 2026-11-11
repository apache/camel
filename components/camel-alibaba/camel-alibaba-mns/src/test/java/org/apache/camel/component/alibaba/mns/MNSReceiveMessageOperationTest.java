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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The consumer receives the messages of a queue, so the operation option offers only the operations of the producer,
 * and a producer asked to receive fails with a message that says so.
 */
class MNSReceiveMessageOperationTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final MNSClient mnsClient = mock(MNSClient.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        context.getRegistry().bind("mnsClient", mnsClient);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .to("alibaba-mns:" + testConfiguration.getProperty("queue")
                            + "?operation=receiveMessage"
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accountEndpoint=" + testConfiguration.getProperty("accountEndpoint")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&mnsClient=#mnsClient");
            }
        };
    }

    @Test
    void operationOffersOnlyTheProducerOperations() throws Exception {
        assertThat(enumsOf("operation")).containsExactly("sendMessage", "deleteMessage", "publishMessage");
    }

    @Test
    void producerFailsToReceiveWithAClearMessage() {
        assertThatThrownBy(() -> template.sendBody("direct:receive", "hello mns"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("receiveMessage is not a producer operation")
                .hasMessageContaining("consumer");
        verifyNoInteractions(cloudQueue);
    }

    private static List<String> enumsOf(String option) throws Exception {
        try (InputStream is = Objects.requireNonNull(MNSReceiveMessageOperationTest.class.getResourceAsStream(
                "/META-INF/org/apache/camel/component/alibaba/mns/alibaba-mns.json"));
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject json = (JsonObject) Jsoner.deserialize(reader);
            JsonObject properties = json.getMap("properties");
            JsonObject property = properties.getMap(option);
            List<?> values = property.getCollection("enum");
            return values.stream().map(String.class::cast).toList();
        }
    }
}
