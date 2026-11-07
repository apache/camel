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
package org.apache.camel.management;

import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * An exchange that fails without a redelivery is not counted as redelivered: when an onException that does not handle
 * the failure takes over, the error handler takes back the redelivery it counted for the failed attempt, also in the
 * counter of the exchange that {@code ExchangeHelper.isRedelivered} reads since CAMEL-26712 (CAMEL-26729).
 */
@DisabledOnOs(OS.AIX)
public class ManagedFailureWithoutRedeliveryTest extends ManagementTestSupport {

    @Test
    public void testAFailureWithoutRedeliveryIsNotARedelivery() throws Exception {
        Exchange failed = template.send("direct:none", e -> e.getMessage().setBody("Hello"));

        assertNotNull(failed.getException());
        getMockEndpoint("mock:seen").expectedMessageCount(1);
        getMockEndpoint("mock:seen").assertIsSatisfied();
        assertFalse(failed.getExchangeExtension().getRedeliveryCounter() > 0);
        assertFalse(failed.getIn().getHeader(Exchange.REDELIVERED, false, Boolean.class));
        ObjectName none = getCamelObjectName(TYPE_ROUTE, "none");
        assertEquals(1L, getMBeanServer().getAttribute(none, "ExchangesFailed"));
        assertEquals(0L, getMBeanServer().getAttribute(none, "Redeliveries"));
    }

    @Test
    public void testAFailureAfterRedeliveriesIsARedelivery() throws Exception {
        Exchange failed = template.send("direct:twice", e -> e.getMessage().setBody("World"));

        assertNotNull(failed.getException());
        ObjectName twice = getCamelObjectName(TYPE_ROUTE, "twice");
        assertEquals(1L, getMBeanServer().getAttribute(twice, "ExchangesFailed"));
        assertEquals(1L, getMBeanServer().getAttribute(twice, "Redeliveries"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the failure is seen by an onException that does not handle it, so the error handler takes back the
                // redelivery it counted for the failed attempt
                from("direct:none").routeId("none")
                        .onException(IllegalStateException.class).to("mock:seen").end()
                        .throwException(new IllegalStateException("Forced"));

                from("direct:twice").routeId("twice")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .throwException(new IllegalStateException("Forced"));
            }
        };
    }
}
