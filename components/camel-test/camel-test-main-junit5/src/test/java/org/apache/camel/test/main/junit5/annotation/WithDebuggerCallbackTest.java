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
package org.apache.camel.test.main.junit5.annotation;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.DebuggerCallback;
import org.apache.camel.test.main.junit5.common.MyConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test class ensuring that the debug mode is enabled if the test class implements the interface
 * {@link DebuggerCallback}.
 */
@CamelMainTest(configurationClasses = MyConfiguration.class)
class WithDebuggerCallbackTest implements DebuggerCallback {

    @EndpointInject("direct:in")
    ProducerTemplate template;

    @BeanInject
    CamelContext camelContext;

    private final AtomicInteger counter = new AtomicInteger();

    @Test
    void shouldInvokeCallbackMethods() {
        assertTrue(camelContext.isDebugging());
        String result = template.requestBody((Object) null, String.class);
        assertEquals("Hello Will!", result);
        assertEquals(4, counter.get());
    }

    @Nested
    class NestedTest {

        @Test
        void shouldSupportNestedTest() throws Exception {
            shouldInvokeCallbackMethods();
        }
    }

    @Override
    public void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label) {
        counter.incrementAndGet();
    }

    @Override
    public void debugAfter(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label,
            long timeTaken) {
        counter.incrementAndGet();
    }
}
