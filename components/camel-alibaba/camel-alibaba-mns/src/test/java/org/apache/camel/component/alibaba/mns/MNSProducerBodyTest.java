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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.CloudTopic;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import com.aliyun.mns.model.TopicMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit6.TestSupport.fileUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The producer sends a String as it is, and bytes, a stream or a file as their bytes: bytes that are not UTF-8 text,
 * such as 0xff, are not changed. A Map is sent as its toString(), and no body fails.
 */
class MNSProducerBodyTest extends CamelTestSupport {

    // not UTF-8 text: 0xff, 0xfe and a lone 0x80 are not valid in UTF-8
    static final byte[] BINARY = { (byte) 0xff, 0x00, (byte) 0xfe, 0x41, (byte) 0x80 };

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final MNSClient mnsClient = mock(MNSClient.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);
    private final CloudTopic cloudTopic = mock(CloudTopic.class);
    private final List<Message> sent = new CopyOnWriteArrayList<>();
    private final List<TopicMessage> published = new CopyOnWriteArrayList<>();

    @TempDir
    Path tempDir;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        when(mnsClient.getQueueRef(testConfiguration.getProperty("queue"))).thenReturn(cloudQueue);
        when(cloudQueue.putMessage(any(Message.class))).thenAnswer(i -> {
            sent.add(i.getArgument(0));
            return new Message();
        });
        when(mnsClient.getTopicRef(testConfiguration.getProperty("topic"))).thenReturn(cloudTopic);
        when(cloudTopic.publishMessage(any(TopicMessage.class))).thenAnswer(i -> {
            published.add(i.getArgument(0));
            return i.getArgument(0);
        });
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
    void stringIsSentAsText() {
        template.sendBody("direct:send", "hello mns é");

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBody()).isEqualTo("hello mns é");
    }

    @Test
    void bytesAreSentUnchanged() {
        template.sendBody("direct:send", BINARY);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBodyAsBytes()).isEqualTo(BINARY);
    }

    @Test
    void streamIsSentAsItsBytes() {
        template.sendBody("direct:send", new ByteArrayInputStream(BINARY));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBodyAsBytes()).isEqualTo(BINARY);
    }

    @Test
    void fileIsSentAsItsBytes() throws Exception {
        File file = Files.write(tempDir.resolve("binary.bin"), BINARY).toFile();

        template.sendBody("direct:send", file);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBodyAsBytes()).isEqualTo(BINARY);
    }

    @Test
    void fileOfTheFileConsumerIsSentAsItsBytes() throws Exception {
        Files.write(tempDir.resolve("binary.bin"), BINARY);

        // the body is the GenericFile of the file consumer
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(tempDir, "?noop=true&initialDelay=0&delay=10"))
                        .to(uri(testConfiguration.getProperty("queue"), "sendMessage"));
            }
        });

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(sent).hasSize(1));
        assertThat(sent.get(0).getMessageBodyAsBytes()).isEqualTo(BINARY);
    }

    @Test
    void bytesArePublishedUnchanged() {
        template.sendBody("direct:publish", BINARY);

        assertThat(published).hasSize(1);
        // a Base64TopicMessage holds the Base64 of the bytes, which MNS delivers
        assertThat(Base64.getDecoder().decode(published.get(0).getMessageBodyAsBytes())).isEqualTo(BINARY);
    }

    @Test
    void stringIsPublishedAsText() {
        template.sendBody("direct:publish", "hello topic é");

        assertThat(published).hasSize(1);
        assertThat(published.get(0).getMessageBody()).isEqualTo("hello topic é");
    }

    @Test
    void mapIsSentAsItsText() {
        template.sendBody("direct:send", Map.of("key", "value"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBody()).isEqualTo("{key=value}");
    }

    @Test
    void listIsSentAsItsText() {
        // such as the body of an aggregate: the type converter would turn a collection into an array element by element
        template.sendBody("direct:send", List.of("a", "b"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBody()).isEqualTo("[a, b]");
    }

    @Test
    void emptyListIsSentAsItsText() {
        template.sendBody("direct:send", List.of());

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageBody()).isEqualTo("[]");
    }

    @Test
    void listIsPublishedAsItsText() {
        template.sendBody("direct:publish", List.of("a", "b"));

        assertThat(published).hasSize(1);
        assertThat(published.get(0).getMessageBody()).isEqualTo("[a, b]");
    }

    @Test
    void noBodyFails() throws Exception {
        assertThatThrownBy(() -> template.sendBody("direct:send", null))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body cannot be null");
        verify(cloudQueue, never()).putMessage(any(Message.class));
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
