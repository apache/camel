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
package org.apache.camel.jsonpath.easypredicate;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A condition written on the path, $.status == 'paid', works as a predicate (CAMEL-24841).
 */
public class EasyJsonPathWithRootPathCBRTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .choice()
                        .when().jsonpath("$.status == 'paid'")
                        .to("mock:paid")
                        .otherwise()
                        .to("mock:other");
            }
        };
    }

    @Test
    public void testPaid() throws Exception {
        getMockEndpoint("mock:paid").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("direct:start", "{\"orderId\": \"ORD-1001\", \"status\": \"paid\"}");
        template.sendBody("direct:start", "{\"orderId\": \"ORD-1003\", \"status\": \"pending\"}");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testAsAnExpressionTheHintSaysSo() {
        // an expression is a path; the comparison written on it is not, and the message says what to write
        Exception e = assertThrows(Exception.class, () -> {
            org.apache.camel.Expression exp = context.resolveLanguage("jsonpath").createExpression("$.status == 'paid'");
            exp.init(context);
            org.apache.camel.Exchange exchange = new org.apache.camel.support.DefaultExchange(context);
            exchange.getMessage().setBody("{\"status\": \"paid\"}");
            exp.evaluate(exchange, Object.class);
        });
        String msg = e.getMessage() + " " + (e.getCause() != null ? e.getCause().getMessage() : "");
        assertTrue(msg.contains("jsonpath is a path, not a comparison"), msg);
        assertTrue(msg.contains("$[?(@.status == 'paid')]"), msg);
        assertTrue(msg.contains("${body[status]} == 'paid'"), msg);
    }

}
