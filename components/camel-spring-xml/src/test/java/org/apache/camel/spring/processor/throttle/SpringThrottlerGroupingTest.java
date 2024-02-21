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
package org.apache.camel.spring.processor.throttle;

import java.util.concurrent.Semaphore;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.throttle.concurrent.ThrottlingGroupingTest;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringThrottlerGroupingTest extends ThrottlingGroupingTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this,
                "org/apache/camel/spring/processor/ThrottlerGroupingTest.xml");
    }

    public static class IncrementProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String key = (String) exchange.getMessage().getHeader("key");
            assertTrue(
                    semaphores.computeIfAbsent(key, k -> new Semaphore(
                            exchange.getMessage().getHeader("throttleValue") == null
                                    ? CONCURRENT_REQUESTS : (Integer) exchange.getMessage().getHeader("throttleValue")))
                            .tryAcquire(),
                    "too many requests for key " + key);
        }
    }

    public static class DecrementProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            semaphores.get(exchange.getMessage().getHeader("key")).release();
        }
    }
}
