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
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The text of the failure handling events says what handled the failure, not "processor: null" for the steps of an
 * onException, which print as their id (none).
 */
class ExchangeFailureHandledEventTextTest extends ContextTestSupport {

    private final List<String> texts = new CopyOnWriteArrayList<>();

    @Test
    void anOnExceptionWithoutIdIsTheErrorHandler() {
        template.sendBody("direct:start", "Hello");

        assertThat(texts).isNotEmpty();
        assertThat(texts).noneMatch(t -> t.contains("processor: null"));
        assertThat(texts).anyMatch(t -> t.endsWith("exchange failed and handled by the error handler"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeFailureHandledEvent
                        || event instanceof CamelEvent.ExchangeFailureHandlingEvent) {
                    texts.add(event.toString());
                }
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true)
                        .log("declined: ${exception.message}")
                        .to("mock:parked");

                from("direct:start")
                        .throwException(new IllegalStateException("card declined"));
            }
        };
    }
}
