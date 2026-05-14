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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(architectures = { "s390x" },
              disabledReason = "This test does not run reliably on s390x (see CAMEL-21438)")
public class AggregateForceCompletionOnStopParallelTest extends AggregateForceCompletionOnStopTest {

    @Override
    @Test
    public void testForceCompletionTrue() {
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
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(2, myCompletionProcessor.getAggregationCount(),
                        "aggregation should have completed"));
    }

    @Override
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
        context.getRouteController().stopRoute("foo");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(2, myCompletionProcessor.getAggregationCount(),
                        "aggregation should have completed"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:forceCompletionTrue").routeId("foo").aggregate(header("id"), new BodyInAggregatingStrategy())
                        .forceCompletionOnStop().completionSize(10)
                        .parallelProcessing().delay(100).process("myCompletionProcessor");

                from("direct:forceCompletionFalse").routeId("bar").aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(10).parallelProcessing().delay(100)
                        .process("myCompletionProcessor");
            }
        };
    }
}
