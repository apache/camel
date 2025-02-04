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
package org.apache.camel.spring.interceptor;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for delayer interceptor configured in spring XML.
 */
public class DelayerInterceptorTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/spring/interceptor/delayerInterceptorTest.xml");
    }

    @Test
    public void testDelayer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        StopWatch watch = new StopWatch();
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message #" + i);
        }
        long delta = watch.taken();

        assertMockEndpointsSatisfied();

        assertTrue(delta > 100, "Should not be that fast to run: " + delta);
        // some OS boxes are slow
        assertTrue(delta < 5000, "Should not take that long to run: " + delta);
    }

}
