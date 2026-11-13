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
package org.apache.camel.impl.console;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The consumer console shows when a scheduled poll consumer last polled, and last polled messages (CAMEL-26715).
 */
public class ConsumerDevConsoleLastPollTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).routeId("files").to("mock:result");
            }
        };
    }

    private JsonObject consumer(DevConsole con) {
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        JsonArray consumers = out.getCollection("consumers");
        return consumers.stream().map(JsonObject.class::cast).filter(c -> "files".equals(c.getString("id")))
                .findFirst().orElseThrow();
    }

    @Test
    public void testLastPoll() throws Exception {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("consumer");

        // polled, but nothing yet
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(consumer(con).getLong("lastPollTimestamp")).isNotNull());
        JsonObject c = consumer(con);
        assertThat(c.getLong("lastPollMessagesTimestamp")).isNull();
        assertThat(c.getInteger("lastPollMessagesCount")).isNull();
        assertThat((String) con.call(DevConsole.MediaType.TEXT)).contains("Last Poll With Messages: none");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader(fileUri(), "Hello", Exchange.FILE_NAME, "a.txt");
        assertMockEndpointsSatisfied();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject j = consumer(con);
            assertThat(j.getLong("lastPollMessagesTimestamp")).isNotNull();
            assertThat(j.getInteger("lastPollMessagesCount")).isEqualTo(1);
            assertThat(j.getLong("lastPollTimestamp")).isGreaterThanOrEqualTo(j.getLong("lastPollMessagesTimestamp"));
        });
        assertThat((String) con.call(DevConsole.MediaType.TEXT))
                .containsPattern("Last Poll: \\S+ ago")
                .containsPattern("Last Poll With Messages: \\S+ ago \\(messages: 1\\)");
    }
}
