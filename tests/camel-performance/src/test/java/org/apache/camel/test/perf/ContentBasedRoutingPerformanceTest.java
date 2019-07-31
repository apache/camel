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
package org.apache.camel.test.perf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class ContentBasedRoutingPerformanceTest extends AbstractBasePerformanceTest {

    private final int count = 30000;

    @Test
    public void testChoiceSimple() throws InterruptedException {
        template.setDefaultEndpointUri("direct:choice-simple");

        // warm up with 20.000 messages so that the JIT compiler kicks in
        execute(20000);

        resetMock(count);

        StopWatch watch = new StopWatch();
        execute(count);

        assertMockEndpointsSatisfied();
        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Test
    public void testChoiceExpression() throws InterruptedException {
        template.setDefaultEndpointUri("direct:choice-expression");

        // warm up with 20.000 messages so that the JIT compiler kicks in
        execute(20000);

        resetMock(count);

        StopWatch watch = new StopWatch();
        execute(count);

        assertMockEndpointsSatisfied();
        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Test
    public void testFilterSimple() throws InterruptedException {
        template.setDefaultEndpointUri("direct:filter-simple");

        // warm up with 20.000 messages so that the JIT compiler kicks in
        execute(20000);

        resetMock(count);

        StopWatch watch = new StopWatch();
        execute(count);

        assertMockEndpointsSatisfied();
        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Test
    public void testFilterExpression() throws InterruptedException {
        template.setDefaultEndpointUri("direct:filter-expression");

        // warm up with 20.000 messages so that the JIT compiler kicks in
        execute(20000);

        resetMock(count);

        StopWatch watch = new StopWatch();
        execute(count);

        assertMockEndpointsSatisfied();
        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:filter-simple")
                    .filter().simple("${body} contains 'xadmin;server1;community#1.0##'")
                        .to("mock:end");

                from("direct:filter-expression")
                    .filter(body().contains("<order><symbol>IBM</symbol><buyerID>asankha</buyerID>"))
                        .to("mock:end");

                from("direct:choice-simple")
                    .choice()
                        .when().simple("${body} contains 'xadmin;server1;community#1.0##'")
                            .to("mock:end");

                from("direct:choice-expression")
                    .choice()
                        .when(body().contains("<order><symbol>IBM</symbol><buyerID>asankha</buyerID>"))
                            .to("mock:end");
            }
        };
    }
}