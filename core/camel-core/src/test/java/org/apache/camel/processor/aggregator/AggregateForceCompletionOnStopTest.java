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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AggregateForceCompletionOnStopTest extends ContextTestSupport {

    @Test
    public void testForceCompletionTrue() throws Exception {
        MyCompletionProcessor myCompletionProcessor
                = context.getRegistry().lookupByNameAndType("myCompletionProcessor", MyCompletionProcessor.class);
        myCompletionProcessor.reset();

        context.getShutdownStrategy().setShutdownNowOnTimeout(true);
        context.getShutdownStrategy().setTimeout(5);

        template.sendBodyAndHeader("direct:forceCompletionTrue", "test1", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionTrue", "test2", "id", "2");
        template.sendBodyAndHeader("direct:forceCompletionTrue", "test3", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionTrue", "test4", "id", "2");

        assertEquals(0, myCompletionProcessor.getAggregationCount(), "aggregation should not have completed yet");
        context.stop();
        assertEquals(2, myCompletionProcessor.getAggregationCount(), "aggregation should have completed");
    }

    @Test
    public void testForceCompletionFalse() throws Exception {
        MyCompletionProcessor myCompletionProcessor
                = context.getRegistry().lookupByNameAndType("myCompletionProcessor", MyCompletionProcessor.class);
        myCompletionProcessor.reset();

        context.getShutdownStrategy().setShutdownNowOnTimeout(true);
        context.getShutdownStrategy().setTimeout(5);

        template.sendBodyAndHeader("direct:forceCompletionFalse", "test1", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionFalse", "test2", "id", "2");
        template.sendBodyAndHeader("direct:forceCompletionFalse", "test3", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionFalse", "test4", "id", "2");

        assertEquals(0, myCompletionProcessor.getAggregationCount(), "aggregation should not have completed yet");
        context.stop();
        assertEquals(0, myCompletionProcessor.getAggregationCount(), "aggregation should not have completed yet");
    }

    @Test
    public void testStopRouteForceCompletionTrue() throws Exception {
        MyCompletionProcessor myCompletionProcessor
                = context.getRegistry().lookupByNameAndType("myCompletionProcessor", MyCompletionProcessor.class);
        myCompletionProcessor.reset();

        context.getShutdownStrategy().setShutdownNowOnTimeout(true);
        context.getShutdownStrategy().setTimeout(5);

        template.sendBodyAndHeader("direct:forceCompletionTrue", "test1", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionTrue", "test2", "id", "2");
        template.sendBodyAndHeader("direct:forceCompletionTrue", "test3", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionTrue", "test4", "id", "2");

        assertEquals(0, myCompletionProcessor.getAggregationCount(), "aggregation should not have completed yet");
        // stopping a route should also force the completion
        context.getRouteController().stopRoute("foo");
        assertEquals(2, myCompletionProcessor.getAggregationCount(), "aggregation should have completed");
    }

    @Test
    public void testStopRouteForceCompletionFalse() throws Exception {
        MyCompletionProcessor myCompletionProcessor
                = context.getRegistry().lookupByNameAndType("myCompletionProcessor", MyCompletionProcessor.class);
        myCompletionProcessor.reset();

        context.getShutdownStrategy().setShutdownNowOnTimeout(true);
        context.getShutdownStrategy().setTimeout(5);

        template.sendBodyAndHeader("direct:forceCompletionFalse", "test1", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionFalse", "test2", "id", "2");
        template.sendBodyAndHeader("direct:forceCompletionFalse", "test3", "id", "1");
        template.sendBodyAndHeader("direct:forceCompletionFalse", "test4", "id", "2");

        assertEquals(0, myCompletionProcessor.getAggregationCount(), "aggregation should not have completed yet");
        context.getRouteController().stopRoute("bar");
        assertEquals(0, myCompletionProcessor.getAggregationCount(), "aggregation should not have completed yet");
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myCompletionProcessor", new MyCompletionProcessor());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:forceCompletionTrue").routeId("foo").aggregate(header("id"), new BodyInAggregatingStrategy())
                        .forceCompletionOnStop().completionSize(10).delay(100)
                        .process("myCompletionProcessor");

                from("direct:forceCompletionFalse").routeId("bar").aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(10).delay(100)
                        .process("myCompletionProcessor");
            }
        };
    }
}
