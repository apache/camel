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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.SimpleFunction;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The watermark expression of a split throws an Error (not a VirtualMachineError): the split completes and fails, as
 * for an exception (CAMEL-26710).
 */
class SplitterWatermarkThrowsErrorTest extends ContextTestSupport {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Test
    void testSplitCompletesWhenTheWatermarkExpressionThrowsAnError() throws Exception {
        getMockEndpoint("mock:split").expectedMessageCount(2);

        Exchange out = template.asyncSend("direct:start", e -> e.getIn().setBody(Arrays.asList("a", "b")))
                .get(5, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context);
        // the item failed with the Error (which the exchange keeps wrapped, as for any Error)
        assertNotNull(out.getException());
        assertInstanceOf(NoClassDefFoundError.class, out.getException().getCause());
        assertFalse(store.containsKey("errorJob"));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a function of the watermark expression that needs a class that is missing
                PluginHelper.getSimpleFunctionRegistry(context).addFunction(new SimpleFunction() {
                    @Override
                    public String getName() {
                        return "boom";
                    }

                    @Override
                    public Object apply(Exchange exchange, Object input) {
                        throw new NoClassDefFoundError("Simulated missing class");
                    }
                });

                from("direct:start")
                        .split(body()).resumeStrategy(new SplitterTestResumeStrategy(store), "errorJob")
                        .watermarkExpression("${function(boom)}")
                        .to("mock:split");
            }
        };
    }
}
