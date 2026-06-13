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
package org.apache.camel.spring.processor.aggregator;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.processor.aggregator.AggregateCompleteAllOnStopTest;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.awaitility.Awaitility.await;

public class SpringAggregateCompleteAllOnStopTest extends AggregateCompleteAllOnStopTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this,
                "org/apache/camel/spring/processor/aggregator/SpringAggregateCompleteAllOnStopTest.xml");
    }

    @Override
    protected void awaitLastMessageInAggregator() throws Exception {
        // The Spring route uses its own internal MemoryAggregationRepository — the Java
        // test's repo field is not wired here. By the time input.assertIsSatisfied() has
        // returned (C passed mock:input) and mock:aggregated has received A+B, the single-
        // threaded seda consumer is processing C between mock:input and the aggregator.
        // stopRoute's graceful shutdown (DefaultShutdownStrategy) tracks in-flight exchanges
        // and blocks until C completes, so completeAllOnStop will then flush C correctly.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> getMockEndpoint("mock:aggregated").getReceivedCounter() >= 1);
    }

}
