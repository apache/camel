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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.processor.OnCompletionListenerTest.Commit;
import org.apache.camel.processor.OnCompletionListenerTest.Event;
import org.apache.camel.processor.OnCompletionListenerTest.Recorder;
import org.apache.camel.processor.OnCompletionListenerTest.Type;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With a custom {@link UnitOfWork} that does not implement
 * {@link UnitOfWork#handoverSynchronization(Exchange, Exchange, Predicate)}, the on completions are handed over as
 * without a listener, and the listener is told that it is not known what moved.
 */
class OnCompletionListenerCustomUnitOfWorkTest extends ContextTestSupport {

    private final Recorder recorder = new Recorder();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, CustomUnitOfWork::create);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("seda:queue");
                from("seda:queue").to("mock:queue");
            }
        };
    }

    @Test
    void testHandoverThatDoesNotTellWhatMoved() throws Exception {
        context.getCamelContextExtension().addOnCompletionListener(recorder);
        Commit commit = new Commit("commit");

        Exchange exchange = createExchangeWithBody("A");
        exchange.getExchangeExtension().addOnCompletion(commit);
        template.send("direct:start", exchange);
        commit.awaitRun();

        Event handover = recorder.events.stream()
                .filter(e -> e.type() == Type.HANDOVER && e.exchange() == exchange)
                .findFirst().orElseThrow();
        assertNull(handover.list());
        assertEquals(1, commit.runs.get());
        // it ran with the copy, as it was handed over
        Event run = recorder.events.stream()
                .filter(e -> e.type() == Type.RUN && e.onCompletion() == commit)
                .findFirst().orElseThrow();
        assertEquals(List.of(run.exchange()), List.of(handover.to()));
    }

    /**
     * A unit of work that delegates to the default one, but uses the default method of the interface to hand over.
     */
    private static final class CustomUnitOfWork {

        static UnitOfWork create(Exchange exchange) {
            DefaultUnitOfWork delegate = new DefaultUnitOfWork(exchange);
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.isDefault() && "handoverSynchronization".equals(method.getName())) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            };
            return (UnitOfWork) Proxy.newProxyInstance(CustomUnitOfWork.class.getClassLoader(),
                    new Class<?>[] { UnitOfWork.class }, handler);
        }
    }
}
