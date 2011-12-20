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
package org.apache.camel.spring.processor.aggregator;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregateProcessor;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * @version 
 */
public class SpringAggregateTimeoutWithExecutorServiceRefTest extends ContextTestSupport {

    private static final int NUM_AGGREGATORS = 4;

    public void testThreadNotUsedForEveryAggregatorWithCustomExecutorService() throws Exception {
        assertTrue("There should not be a thread for every aggregator when using a shared thread pool", 
                aggregateThreadsCount() < NUM_AGGREGATORS);
        
        // sanity check to make sure were testing routes that work
        for (int i = 0; i < NUM_AGGREGATORS; ++i) {
            MockEndpoint result = getMockEndpoint("mock:result" + i);
            // by default the use latest aggregation strategy is used so we get message 4
            result.expectedBodiesReceived("Message 4");
        }
        for (int i = 0; i < NUM_AGGREGATORS; ++i) {
            for (int j = 0; j < 5; j++) {
                template.sendBodyAndHeader("direct:start" + i, "Message " + j, "id", "1");
            }
        }
        assertMockEndpointsSatisfied();
    }

    public static int aggregateThreadsCount() {
        int count = 0;
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        Thread[] threads = new Thread[threadGroup.activeCount()];
        threadGroup.enumerate(threads);
        for (Thread thread : threads) {
            if (thread.getName().contains(AggregateProcessor.AGGREGATE_TIMEOUT_CHECKER)) {
                ++count;
            }
        }
        return count;
    }
    
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/aggregator/SpringAggregateTimeoutWithExecutorServiceRefTest.xml");
    }

}