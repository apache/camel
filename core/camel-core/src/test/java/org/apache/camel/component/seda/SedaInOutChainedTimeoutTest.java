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
package org.apache.camel.component.seda;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SedaInOutChainedTimeoutTest extends ContextTestSupport {

    @Test
    public void testSedaInOutChainedTimeout() {
        // time timeout after 2 sec should trigger an immediate reply
        StopWatch watch = new StopWatch();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("seda:a?timeout=5000", "Hello World"), "Should have thrown an exception");
        ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
        assertEquals(2000, cause.getTimeout());

        long delta = watch.taken();

        assertTrue(delta < 4000, "Should be faster than 4000 millis, was: " + delta);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("seda:a").to("mock:a")
                        // this timeout will trigger an exception to occur
                        .to("seda:b?timeout=2000").to("mock:a2");

                from("seda:b").to("mock:b").delay(3000).transform().constant("Bye World");
            }
        };
    }
}
