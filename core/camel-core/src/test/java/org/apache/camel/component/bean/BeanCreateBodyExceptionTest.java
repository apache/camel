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
package org.apache.camel.component.bean;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanCreateBodyExceptionTest extends ContextTestSupport {

    @Test
    public void testCreateBodyFirstTimeException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.send("direct:start", e -> {
            e.setIn(new DefaultMessage(e) {

                private boolean created;

                @Override
                protected Object createBody() {
                    if (!created) {
                        created = true;
                        throw new IllegalArgumentException("Forced internal error");
                    } else {
                        return "Hello World";
                    }
                }
            });
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateBodyAlwaysException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.send("direct:start", e -> {
            e.setIn(new DefaultMessage(e) {
                @Override
                protected Object createBody() {
                    throw new IllegalArgumentException("Forced internal error");
                }
            });
        });

        assertThrows(AssertionError.class, () -> assertMockEndpointsSatisfied());

        // mock:dead should have as failure as createBody fails so cannot do defensive copy in mock
        assertEquals(1, getMockEndpoint("mock:dead").getFailures().size());
        Throwable e = getMockEndpoint("mock:dead").getFailures().get(0);
        assertNotNull(e);
        assertEquals("Forced internal error", e.getMessage());
    }

    @Test
    public void testProducerTemplateCreateBodyAlwaysException() throws Exception {
        template.send("seda:empty", e -> {
            e.setIn(new DefaultMessage(e) {
                @Override
                protected Object createBody() {
                    throw new IllegalArgumentException("Forced internal error");
                }
            });
        });
    }

    @Test
    public void testConsumerTemplateCreateBodyAlwaysException() throws Exception {
        final AtomicBoolean fail = new AtomicBoolean();

        template.send("seda:empty", e -> {
            e.setIn(new DefaultMessage(e) {

                @Override
                protected Object createBody() {
                    if (fail.get()) {
                        throw new IllegalArgumentException("Forced internal error");
                    } else {
                        return null;
                    }
                }

                @Override
                public DefaultMessage newInstance() {
                    // dont copy so we can preserve this message instance that has the fail mode in createBody
                    return this;
                }
            });
        });

        // turn on fail mode
        fail.set(true);

        Exception e = assertThrows(Exception.class,
                () -> consumer.receiveBody("seda:empty", 10000),
                "Should throw exception");

        assertIsInstanceOf(IllegalArgumentException.class, e);
        assertEquals("Forced internal error", e.getMessage());

        fail.set(false);
        assertDoesNotThrow(() -> consumer.receiveBody("seda:empty", 10000), "");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .errorHandler(deadLetterChannel("mock:dead"))
                        .bean(BeanCreateBodyExceptionTest.class, "callMe")
                        .to("mock:result");
            }
        };
    }

    public String callMe(String body) {
        return "Hello " + body;
    }
}
