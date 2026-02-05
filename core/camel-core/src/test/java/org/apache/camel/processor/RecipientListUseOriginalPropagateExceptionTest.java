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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RecipientListUseOriginalPropagateExceptionTest extends ContextTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {
            "throw1",
            "throw2",
    })
    public void testWithPropagation(String body) throws Exception {

        getMockEndpoint("mock:recipient1").expectedMessageCount(body.contains("throw1") ? 0 : 1);
        getMockEndpoint("mock:recipient2").expectedMessageCount(body.contains("throw2") ? 0 : 1);

        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", body));

        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertInstanceOf(RuntimeException.class, rootCause);

        if (body.contains("throw1")) {
            assertEquals("recipient1", rootCause.getMessage());
        }
        if (body.contains("throw2")) {
            assertEquals("recipient2", rootCause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:start")
                        .errorHandler(noErrorHandler())
                        .recipientList(constant("direct:recipient1,direct:recipient2"))
                        .aggregationStrategy(new UseOriginalAggregationStrategy(true))
                        .to("mock:result");

                from("direct:recipient1")
                        .log("recipient1")
                        .choice()
                        .when(bodyAs(String.class).contains("throw1"))
                        .process(
                                exchange -> {
                                    exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new RuntimeException("recipient1Caught"));
                                    throw new RuntimeException("recipient1");
                                })
                        .end()
                        .setBody(constant("recipient1"))
                        .to("mock:recipient1");

                from("direct:recipient2")
                        .log("recipient2")
                        .choice()
                        .when(bodyAs(String.class).contains("throw2"))
                        .process(
                                exchange -> {
                                    throw new RuntimeException("recipient2");
                                })
                        .end()
                        .setBody(constant("recipient2"))
                        .to("mock:recipient2");
            }
        };
    }

}
