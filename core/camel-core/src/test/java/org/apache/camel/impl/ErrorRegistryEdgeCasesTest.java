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
package org.apache.camel.impl;

import java.io.IOException;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorRegistryEdgeCasesTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        context.setMessageHistory(true);
        return context;
    }

    private void send(String uri, Object body) {
        try {
            template.sendBody(uri, body);
        } catch (Exception e) {
            // the tests are about what the registry recorded
        }
    }

    private List<BacklogErrorEventMessage> entries() {
        return List.copyOf(context.getErrorRegistry().browse());
    }

    @Test
    public void testOnExceptionNotHandledIsNotRecordedAsHandled() {
        send("direct:notHandled", "a");
        assertThat(entries()).singleElement().satisfies(e -> {
            assertThat(e.getException()).isInstanceOf(IllegalArgumentException.class);
            assertThat(e.isHandled()).isFalse();
        });
    }

    @Test
    public void testDoCatchThatThrowsAgainIsNotRecordedAsHandled() {
        send("direct:rethrow", "a");
        assertThat(entries()).extracting(BacklogErrorEventMessage::getExceptionType)
                .containsExactlyInAnyOrder(IllegalStateException.class.getName(), IllegalArgumentException.class.getName());
        assertThat(entries()).allSatisfy(e -> assertThat(e.isHandled()).isFalse());
    }

    @Test
    public void testFailureAfterHandledFailureIsRecorded() {
        send("direct:second", "a");
        assertThat(entries()).hasSize(2);
        assertThat(entries()).anySatisfy(e -> {
            assertThat(e.getException()).isInstanceOf(IllegalStateException.class);
            assertThat(e.getToNode()).isEqualTo("t2");
            assertThat(e.isHandled()).isFalse();
        });
        assertThat(entries()).anySatisfy(e -> {
            assertThat(e.getException()).isInstanceOf(IllegalArgumentException.class);
            assertThat(e.isHandled()).isTrue();
        });
    }

    @Test
    public void testFailuresOfSplitPartsAreAllRecorded() {
        send("direct:split", "a,b,c");
        assertThat(entries()).extracting(BacklogErrorEventMessage::getToNode).containsExactlyInAnyOrder("n1", "n2");
    }

    @Test
    public void testFailureInOnCompletionKeepsTheRouteFailure() {
        send("direct:oc", "a");
        assertThat(entries()).extracting(BacklogErrorEventMessage::getExceptionType)
                .containsExactlyInAnyOrder(IllegalArgumentException.class.getName(), IOException.class.getName());
    }

    @Test
    public void testNoErrorHandlerRecordsTheRouteThatFailed() {
        send("direct:a", "a");
        assertThat(entries()).singleElement().satisfies(e -> {
            assertThat(e.getRouteId()).isEqualTo("b");
            assertThat(e.getToNode()).isEqualTo("boom");
        });
    }

    @Test
    public void testClearOfRouteResetsRepeatCount() {
        for (int i = 0; i < 5; i++) {
            send("direct:storm", "a");
        }
        context.getErrorRegistry().forRoute("storm").clear();
        send("direct:storm", "a");
        assertThat(entries()).singleElement().satisfies(e -> assertThat(e.getRepeatCount()).isEqualTo(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:notHandled").routeId("nh")
                        .onException(IllegalArgumentException.class).to("mock:log").end()
                        .throwException(new IllegalArgumentException("x"));
                from("direct:rethrow").routeId("rt")
                        .doTry().throwException(new IllegalArgumentException("x"))
                        .doCatch(IllegalArgumentException.class).throwException(new IllegalStateException("wrapped"))
                        .end();
                from("direct:second").routeId("second")
                        .doTry().throwException(new IllegalArgumentException("first")).id("t1")
                        .doCatch(IllegalArgumentException.class).end()
                        .throwException(new IllegalStateException("second")).id("t2");
                from("direct:split").routeId("split")
                        .errorHandler(deadLetterChannel("mock:dead"))
                        .split(body().tokenize(","))
                        .choice()
                        .when(body().isEqualTo("a")).throwException(new IllegalArgumentException("a")).id("n1")
                        .when(body().isEqualTo("c")).throwException(new NullPointerException("c")).id("n2")
                        .end();
                from("direct:oc").routeId("oc")
                        .onCompletion().onFailureOnly().throwException(new IOException("alert down")).end()
                        .throwException(new IllegalArgumentException("real"));
                from("direct:a").routeId("a").errorHandler(noErrorHandler()).to("direct:b");
                from("direct:b").routeId("b").errorHandler(noErrorHandler())
                        .throwException(new IllegalArgumentException("boom")).id("boom");
                from("direct:storm").routeId("storm").throwException(new IllegalArgumentException("storm"));
            }
        };
    }
}
