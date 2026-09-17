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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PooledExchangeSplitLeakTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        return camelContext;
    }

    @Test
    public void testSplitChildrenDoNotLeakStateBetweenMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        template.sendBody("direct:start", List.of("first"));
        template.sendBody("direct:start", List.of("second"));

        mock.assertIsSatisfied();

        // the child of the first message carries the state set by the processor
        assertEquals("from-first", mock.getExchanges().get(0).getProperty("leakProp"));
        assertEquals("from-first", mock.getExchanges().get(0).getVariable("leak"));
        // the child of the second message is a reused pooled exchange; it must not carry the first child's state
        assertNull(mock.getExchanges().get(1).getProperty("leakProp"));
        assertNull(mock.getExchanges().get(1).getVariable("leak"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").split(body())
                        .process(e -> {
                            if ("first".equals(e.getMessage().getBody())) {
                                e.setProperty("leakProp", "from-first");
                                e.setVariable("leak", "from-first");
                            }
                        })
                        .to("mock:split");
            }
        };
    }
}
