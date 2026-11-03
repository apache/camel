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
package org.apache.camel.component.jgroups;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body that has no serialized form (a stream, a stream cache, a file from the file component or a Path) is sent as
 * its content, and another body that is not Serializable fails with a hint to convert it.
 */
public class JGroupsProducerBodyTest extends CamelTestSupport {

    static final String CLUSTER_NAME = "JGroupsProducerBodyTest";

    static final String CONTENT = "Hello JGroups";

    @TempDir
    Path inbox;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jgroups:" + CLUSTER_NAME + "?acceptAllObjects=true").to("mock:received");

                from("direct:cached").streamCaching().to("jgroups:" + CLUSTER_NAME);
                from("direct:stream").noStreamCaching().to("jgroups:" + CLUSTER_NAME);
                from("file:" + inbox + "?initialDelay=0&delay=10").noAutoStartup().routeId("file")
                        .to("jgroups:" + CLUSTER_NAME);
            }
        };
    }

    @Test
    public void shouldSendStreamCacheContent() throws Exception {
        MockEndpoint received = getMockEndpoint("mock:received");
        received.expectedMessageCount(1);

        template.send("direct:cached", exchange -> {
            exchange.getIn().setBody(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)));
        });

        received.assertIsSatisfied();
        assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8),
                received.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void shouldKeepStreamCacheReadableAfterSend() throws Exception {
        getMockEndpoint("mock:received").expectedMessageCount(1);

        Object body = template.send("direct:cached", exchange -> {
            exchange.getIn().setBody(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)));
        }).getMessage().getBody();

        MockEndpoint.assertIsSatisfied(context);
        StreamCache cache = assertInstanceOf(StreamCache.class, body);
        assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8),
                context.getTypeConverter().convertTo(byte[].class, cache));
    }

    @Test
    public void shouldSendInputStreamContent() throws Exception {
        MockEndpoint received = getMockEndpoint("mock:received");
        received.expectedMessageCount(1);

        template.sendBody("direct:stream", new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)));

        received.assertIsSatisfied();
        assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8),
                received.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void shouldSendReaderContent() throws Exception {
        MockEndpoint received = getMockEndpoint("mock:received");
        received.expectedMessageCount(1);

        template.sendBody("direct:stream", new StringReader(CONTENT));

        received.assertIsSatisfied();
        assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8),
                received.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void shouldSendFileContent() throws Exception {
        MockEndpoint received = getMockEndpoint("mock:received");
        received.expectedMessageCount(1);

        Files.writeString(inbox.resolve("hello.txt"), CONTENT);
        context.getRouteController().startRoute("file");

        received.assertIsSatisfied();
        assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8),
                received.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void shouldSendPathContent() throws Exception {
        MockEndpoint received = getMockEndpoint("mock:received");
        received.expectedMessageCount(1);

        Path file = Files.writeString(inbox.resolve("hello.txt"), CONTENT);
        template.sendBody("direct:stream", file);

        received.assertIsSatisfied();
        assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8),
                received.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void shouldFailWithHintForBodyThatIsNotSerializable() throws Exception {
        getMockEndpoint("mock:received").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:stream", new NotSerializableBody()));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, e.getCause());
        assertTrue(cause.getMessage().contains(NotSerializableBody.class.getCanonicalName()), cause.getMessage());
        assertTrue(cause.getMessage().contains("convert it first"), cause.getMessage());
        MockEndpoint.assertIsSatisfied(context);
    }

    static final class NotSerializableBody {
        @Override
        public String toString() {
            return "NotSerializableBody";
        }
    }

}
