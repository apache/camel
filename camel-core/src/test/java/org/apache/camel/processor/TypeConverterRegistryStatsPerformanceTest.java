/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class TypeConverterRegistryStatsPerformanceTest extends ContextTestSupport {

    private int size = 1000;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setTypeConverterStatisticsEnabled(true);
        return context;
    }

    public void testTransform() throws Exception {
        long noop = context.getTypeConverterRegistry().getStatistics().getNoopCounter();
        long attempt = context.getTypeConverterRegistry().getStatistics().getAttemptCounter();
        long failed = context.getTypeConverterRegistry().getStatistics().getFailedCounter();
        long hit = context.getTypeConverterRegistry().getStatistics().getHitCounter();
        long miss = context.getTypeConverterRegistry().getStatistics().getMissCounter();

        getMockEndpoint("mock:result").expectedMessageCount(size);

        for (int i = 0; i < size; i++) {
            template.sendBody("direct:start", "World");
        }

        assertMockEndpointsSatisfied();

        long noop2 = context.getTypeConverterRegistry().getStatistics().getNoopCounter();
        long attempt2 = context.getTypeConverterRegistry().getStatistics().getAttemptCounter();
        long failed2 = context.getTypeConverterRegistry().getStatistics().getFailedCounter();
        long hit2 = context.getTypeConverterRegistry().getStatistics().getHitCounter();
        long miss2 = context.getTypeConverterRegistry().getStatistics().getMissCounter();

        log.info("Noop: before={}, after={}, delta={}", new Object[]{noop, noop2, noop2 - noop});
        log.info("Attempt: before={}, after={}, delta={}", new Object[]{attempt, attempt2, attempt2 - attempt});
        log.info("Failed: before={}, after={}, delta={}", new Object[]{failed, failed2, failed2 - failed});
        log.info("Hit: before={}, after={}, delta={}", new Object[]{hit, hit2, hit2 - hit});
        log.info("Miss: before={}, after={}, delta={}", new Object[]{miss, miss2, miss2 - miss});
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .transform().method(TypeConverterRegistryStatsPerformanceTest.class, "transformMe")
                    .bean(TypeConverterRegistryStatsPerformanceTest.class, "transformMeAlso")
                    .to("mock:result");
            }
        };
    }

    public String transformMe(String in) {
        return "Hello " + in;
    }

    public String transformMeAlso(String in) {
        return "Bye " + in;
    }
}
