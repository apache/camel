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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An exchange that fails with a {@link VirtualMachineError} sends an {@link ExchangeFailedEvent} that carries it, so an
 * application can react to it with an event notifier, such as to shut down gracefully (CAMEL-27515).
 */
public class VirtualMachineErrorEventTest extends ContextTestSupport {

    private final List<Throwable> fatalErrors = new CopyOnWriteArrayList<>();

    @Test
    public void exchangeFailedEventCarriesTheVirtualMachineError() {
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof ExchangeFailedEvent failed) {
                    VirtualMachineError fatal = ObjectHelper.getVirtualMachineError(failed.getExchange().getException());
                    if (fatal != null) {
                        fatalErrors.add(fatal);
                    }
                }
            }
        });

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Hello"));

        assertEquals(1, fatalErrors.size());
        assertInstanceOf(StackOverflowError.class, fatalErrors.get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        })
                        .to("mock:result");
            }
        };
    }
}
