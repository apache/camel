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
package org.apache.camel.component.timer;

import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class TimerRouteTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(TimerRouteTest.class);
    private MyBean bean = new MyBean();

    public void testTimerInvokesBeanMethod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        assertMockEndpointsSatisfied();

        assertTrue("Should have fired 2 or more times was: " + bean.counter.get(), bean.counter.get() >= 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("timer://foo?fixedRate=true&delay=0&period=10")
                    .log("Fired timer")
                    .to("bean:myBean", "mock:result");
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", bean);
        return answer;
    }

    public static class MyBean {
        public AtomicInteger counter = new AtomicInteger(0);

        public void someMethod() {
            LOG.debug("Invoked someMethod()");
            counter.incrementAndGet();
        }
    }
}
