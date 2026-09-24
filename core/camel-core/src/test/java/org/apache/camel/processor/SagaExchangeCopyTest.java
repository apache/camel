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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The exchanges created by split and multicast are copies of the exchange of the saga, and belong to the same saga.
 */
class SagaExchangeCopyTest extends ContextTestSupport {

    @Test
    void testSplitMandatoryStepsJoinSaga() throws Exception {
        assertItemsCompensated("direct:split", 3);
    }

    @Test
    void testMulticastRequiredStepsJoinSaga() throws Exception {
        assertItemsCompensated("direct:multicast", 2);
    }

    private void assertItemsCompensated(String uri, int items) throws Exception {
        getMockEndpoint("mock:compensate").expectedMessageCount(1);
        getMockEndpoint("mock:item").expectedMessageCount(items);
        getMockEndpoint("mock:compensate-item").expectedMessageCount(items);
        getMockEndpoint("mock:complete-item").expectedMessageCount(0);

        CamelExecutionException ex
                = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, List.of("a", "b", "c")));

        MockEndpoint.assertIsSatisfied(context);
        assertEquals("payment declined", ex.getCause().getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addService(new InMemorySagaService());

                from("direct:split")
                        .saga().compensation("mock:compensate")
                        .split(body()).to("direct:mandatory-item").end()
                        .throwException(new IllegalStateException("payment declined"));

                from("direct:multicast")
                        .saga().compensation("mock:compensate")
                        .multicast().to("direct:required-item", "direct:required-item").end()
                        .throwException(new IllegalStateException("payment declined"));

                from("direct:mandatory-item")
                        .saga().propagation(SagaPropagation.MANDATORY)
                        .compensation("mock:compensate-item").completion("mock:complete-item")
                        .to("mock:item");

                from("direct:required-item")
                        .saga().propagation(SagaPropagation.REQUIRED)
                        .compensation("mock:compensate-item").completion("mock:complete-item")
                        .to("mock:item");
            }
        };
    }
}
