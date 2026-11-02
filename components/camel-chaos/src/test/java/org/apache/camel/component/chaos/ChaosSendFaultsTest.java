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
package org.apache.camel.component.chaos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The faults at the sends to an endpoint: error, drop and corrupt (delay is in {@link ChaosDelayTest}).
 */
class ChaosSendFaultsTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target").to("mock:after");
                from("direct:dynamic").routeId("dynamic").toD("mock:${header.to}");
                from("direct:list").routeId("list").recipientList(header("to"));
            }
        };
    }

    @Test
    void errorWithoutExceptionIsAChaosException() throws Exception {
        String id = chaos().addRule("endpoint=mock:target error");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.expectedMessageCount(0);
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(0);

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("hello"));

        assertThat(out.getException()).isInstanceOf(ChaosException.class)
                .hasMessage("Chaos error of rule " + id);
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void errorWithExceptionClassAndMessage() {
        chaos().addRule("endpoint=mock:target error exception=" + IOException.class.getName() + " message=\"disk full\"");

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("hello"));

        assertThat(out.getException()).isExactlyInstanceOf(IOException.class).hasMessage("disk full");
    }

    @Test
    void errorAtAProducerTemplateSendIsReturnedToTheCaller() {
        chaos().addRule("endpoint=mock:target error message=boom");

        assertThatThrownBy(() -> template.sendBody("mock:target", "hello"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(ChaosException.class);
    }

    @Test
    void ruleMatchesSendsOfToDAndRecipientListToo() throws Exception {
        String id = chaos().addRule("endpoint=mock:other* drop");
        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:dynamic", "a", "to", "other");
        template.sendBodyAndHeader("direct:list", "b", "to", "mock:other");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(2);
    }

    @Test
    void dropReportsSuccessWithoutCallingTheProducer() throws Exception {
        String id = chaos().addRule("endpoint=mock:target drop");
        getMockEndpoint("mock:target").expectedMessageCount(0);
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedBodiesReceived("hello");

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("hello"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void theRulesStayWhenTheRoutesAreReloaded() throws Exception {
        String id = chaos().addRule("endpoint=mock:target error");
        assertThat(template.send("direct:start", e -> e.getMessage().setBody("a")).getException())
                .isInstanceOf(ChaosException.class);

        context.getRouteController().stopRoute("start");
        context.removeRoute("start");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target").to("mock:after");
            }
        });

        assertThat(template.send("direct:start", e -> e.getMessage().setBody("b")).getException())
                .isInstanceOf(ChaosException.class);
        assertThat(fired(id, "send")).isEqualTo(2);
    }

    @Test
    void otherEndpointsAreNotTouched() throws Exception {
        String id = chaos().addRule("endpoint=mock:nothing* error");
        getMockEndpoint("mock:target").expectedBodiesReceived("hello");
        getMockEndpoint("mock:after").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(ruleReport(id).getLong("matches")).isZero();
    }

    @Test
    void corruptHeadersChangesTheUserHeadersOnly() throws Exception {
        String id = chaos().addRule("endpoint=mock:target corrupt mode=headers");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:start", "hello",
                Map.of("orderId", "42", "CamelFileName", "a.txt"));

        MockEndpoint.assertIsSatisfied(context);
        Exchange received = target.getReceivedExchanges().get(0);
        assertThat(received.getMessage().getHeader("orderId", String.class)).isNotEqualTo("42").hasSize(16);
        assertThat(received.getMessage().getHeader("CamelFileName")).isEqualTo("a.txt");
        assertThat(received.getMessage().getBody()).isEqualTo("hello");
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void corruptHeadersWithoutUserHeadersIsSkipped() throws Exception {
        String id = chaos().addRule("endpoint=mock:target corrupt mode=headers");
        getMockEndpoint("mock:target").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isZero();
        assertThat(ruleReport(id).getLong("skipped")).isEqualTo(1);
    }

    @Test
    void corruptCharsetGivesTheTextInUtf16() throws Exception {
        String id = chaos().addRule("endpoint=mock:target corrupt mode=charset");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.expectedMessageCount(1);

        template.sendBody("direct:start", "hello");

        MockEndpoint.assertIsSatisfied(context);
        byte[] body = target.getReceivedExchanges().get(0).getMessage().getBody(byte[].class);
        assertThat(body).isEqualTo("hello".getBytes(StandardCharsets.UTF_16));
        assertThat(new String(body, StandardCharsets.UTF_8)).isNotEqualTo("hello").contains("\u0000");
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void corruptCharsetOnAStreamIsSkipped() throws Exception {
        String id = chaos().addRule("endpoint=mock:target corrupt mode=charset");
        getMockEndpoint("mock:target").expectedMessageCount(1);

        template.sendBody("direct:start", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
        assertThat(ruleReport(id).getLong("skipped")).isEqualTo(1);
    }

    @Test
    void corruptCharsetReadsALocalFileAsText(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("order.txt"), "an order");
        String id = chaos().addRule("endpoint=mock:target corrupt mode=charset");
        getMockEndpoint("mock:target").expectedMessageCount(2);

        template.sendBody("direct:start", file.toFile());
        template.sendBody("direct:start", file);

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(2);
        for (Exchange received : getMockEndpoint("mock:target").getReceivedExchanges()) {
            assertThat(received.getMessage().getBody()).isEqualTo("an order".getBytes(StandardCharsets.UTF_16));
        }
        // the file itself is not changed
        assertThat(Files.readString(file)).isEqualTo("an order");
    }

    @Test
    void componentTargetMatchesTheScheme() throws Exception {
        String id = chaos().addRule("component=mock error");
        getMockEndpoint("mock:target").expectedMessageCount(0);

        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException())
                .isInstanceOf(ChaosException.class);
        // direct:start is not a mock endpoint
        assertThat(ruleReport(id).getLong("matches")).isEqualTo(1);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void truncateEndsTheStreamAfterHalfOfItsBytes() throws Exception {
        String id = chaos().addRule("endpoint=mock:target corrupt mode=truncate");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.expectedMessageCount(2);

        template.sendBody("direct:start", new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8)));
        template.sendBody("direct:start", new InputStreamCache("abcdef".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
        Object first = target.getReceivedExchanges().get(0).getMessage().getBody();
        assertThat(first).isInstanceOf(InputStream.class);
        assertThat(((InputStream) first).readAllBytes()).isEqualTo("01234".getBytes(StandardCharsets.UTF_8));
        Object second = target.getReceivedExchanges().get(1).getMessage().getBody();
        // a stream cache stays a stream cache
        assertThat(second).isInstanceOf(InputStreamCache.class);
        assertThat(((InputStream) second).readAllBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(fired(id, "send")).isEqualTo(2);
    }

    @Test
    void truncateOnTextIsSkipped() throws Exception {
        String id = chaos().addRule("endpoint=mock:target corrupt mode=truncate");
        getMockEndpoint("mock:target").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(ruleReport(id).getLong("skipped")).isEqualTo(1);
    }

    @Test
    void everyNthAndUntilCount() throws Exception {
        String id = chaos().addRule("endpoint=mock:target error rate=every:2 until=4");
        int failed = 0;
        for (int i = 0; i < 6; i++) {
            if (template.send("direct:start", e -> e.getMessage().setBody("x")).getException() != null) {
                failed++;
            }
        }
        // matches 2 and 4 fire; the rule ends after 4 matches
        assertThat(failed).isEqualTo(2);
        assertThat(ruleReport(id).getLong("matches")).isEqualTo(4);
        assertThat(ruleReport(id).getString("state")).isEqualTo("ended");
    }

    @Test
    void theFirstRuleThatFiresDecides() throws Exception {
        String drop = chaos().addRule("endpoint=mock:target drop");
        String error = chaos().addRule("endpoint=mock:target error");
        getMockEndpoint("mock:after").expectedMessageCount(1);

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(drop, "send")).isEqualTo(1);
        assertThat(ruleReport(error).getLong("matches")).isZero();
    }

    @Test
    void removedRuleStopsFiring() throws Exception {
        String id = chaos().addRule("endpoint=mock:target error");
        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException()).isNotNull();
        assertThat(chaos().removeRule(id)).isTrue();
        assertThat(chaos().removeRule(id)).isFalse();

        getMockEndpoint("mock:target").expectedMessageCount(1);
        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(ruleReport(id).getString("state")).isEqualTo("removed");
    }

    @Test
    void changedRuleKeepsItsId() {
        String id = chaos().addRule("endpoint=mock:target error");
        chaos().changeRule(id, "endpoint=mock:target error exception=" + IOException.class.getName());

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isExactlyInstanceOf(IOException.class);
        assertThatThrownBy(() -> chaos().changeRule("r99", "endpoint=x error"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no chaos rule r99");
        assertThatThrownBy(() -> chaos().changeRule(id, "step=x drop"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not offered");
        // the replaced rule stays in the report
        assertThat(chaos().report().toText()).contains(id + " replaced: endpoint=mock:target error");
    }
}
