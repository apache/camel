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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ServiceSupport;

/**
 * @version 
 */
public class RouteSuspendResumeWarmUpTest extends ContextTestSupport {

    private MyService service = new MyService();

    public void testRouteSuspendResumeWarmUpTest() throws Exception {
        assertEquals("start", service.getState());

        context.suspendRoute("foo");
        // should keep this state as we are only suspending the consumer
        assertEquals("start", service.getState());

        context.resumeRoute("foo");
        // should keep this state as we are only suspending the consumer
        assertEquals("start", service.getState());

        context.stop();

        // now its stopped
        assertEquals("stop", service.getState());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").process(service).to("mock:result");
            }
        };
    }

    private static class MyService extends ServiceSupport implements Processor {

        private volatile String state;

        public void process(Exchange exchange) throws Exception {
            // noop
        }

        protected void doStart() throws Exception {
            state = "start";
        }

        protected void doStop() throws Exception {
            state = "stop";
        }

        public String getState() {
            return state;
        }
    }
}
