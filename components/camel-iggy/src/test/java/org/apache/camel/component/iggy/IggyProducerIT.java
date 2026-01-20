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
package org.apache.camel.component.iggy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.iggy.message.Message;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Iggy 0.6.0+ requires io_uring which is not available on CI environments")
public class IggyProducerIT extends IggyTestBase {

    @Test
    public void testString() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        contextExtension.getProducerTemplate().sendBody("direct:start", "Hello World");

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    public void testListOfStrings() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        contextExtension.getProducerTemplate().sendBody("direct:start", List.of("Hello", "World"));

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    public void testListOfMessages() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        contextExtension.getProducerTemplate().sendBody("direct:start", List.of(Message.of("Hello"), Message.of("World")));

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    public void testMessage() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        contextExtension.getProducerTemplate().sendBody("direct:start", Message.of("Hello World"));

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    public void testBytes() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        contextExtension.getProducerTemplate().sendBody("direct:start", "Hello World".getBytes());

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    public void testComplexObject() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        record Character(String name) {
        }

        Map data = new HashMap();
        data.put("test", new Character("pippo"));

        contextExtension.getProducerTemplate().sendBody("direct:start", data);

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();

        Assertions.assertThat(pollMessagesPayloadsAsString())
                .containsAnyElementsOf(List.of("{test=Character[name=pippo]}"));
    }

    @Test
    public void testStreamAndTopicOverride() throws Exception {
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(1);

        contextExtension.getProducerTemplate().sendBodyAndHeaders("direct:start", "Hello World",
                Map.of(IggyConstants.STREAM_OVERRIDE, "stream-override",
                        IggyConstants.TOPIC_OVERRIDE, "topic-override"));

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();

        Assertions.assertThat(pollMessagesPayloadsAsStringFromCustomStreamTopic("stream-override", "topic-override"))
                .containsAnyElementsOf(List.of("Hello World"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("iggy:%s?username=%s&password=%s&streamName=%s&host=%s&port=%d",
                                TOPIC, iggyService.username(), iggyService.password(), STREAM,
                                iggyService.host(), iggyService.port())
                        .to("mock:result");
            }
        };
    }
}
