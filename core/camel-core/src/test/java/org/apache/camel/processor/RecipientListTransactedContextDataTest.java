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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Each transaction must get its own transaction context data when a transacted exchange goes through a recipient list.
 */
public class RecipientListTransactedContextDataTest extends ContextTestSupport {

    private final List<Map<?, ?>> data = new ArrayList<>();

    @Test
    public void testTransactionContextDataIsNotShared() {
        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");

        assertEquals(2, data.size());
        assertNotNull(data.get(0));
        assertNotNull(data.get(1));
        assertNotSame(data.get(0), data.get(1), "two transactions must not share the transaction context data");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(e -> e.getExchangeExtension().setTransacted(true))
                        .recipientList(constant("direct:a"));

                from("direct:a")
                        .process(e -> data.add(e.getProperty(Exchange.TRANSACTION_CONTEXT_DATA, Map.class)));
            }
        };
    }
}
