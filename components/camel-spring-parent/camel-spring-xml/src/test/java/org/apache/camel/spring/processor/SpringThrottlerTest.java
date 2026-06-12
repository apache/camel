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
package org.apache.camel.spring.processor;

import java.util.concurrent.Semaphore;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.throttle.concurrent.ConcurrentRequestsThrottlerTest;
import org.apache.camel.support.SynchronizationAdapter;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringThrottlerTest extends ConcurrentRequestsThrottlerTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this,
                "org/apache/camel/spring/processor/throttler.xml");
    }

    public static class IncrementProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Semaphore s = semaphore;
            assertTrue(s.tryAcquire(), "too many requests");
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange ex) {
                    s.release();
                }

                @Override
                public void onFailure(Exchange ex) {
                    s.release();
                }
            });
        }
    }

    public static class RuntimeExceptionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            throw new RuntimeException();
        }
    }
}
