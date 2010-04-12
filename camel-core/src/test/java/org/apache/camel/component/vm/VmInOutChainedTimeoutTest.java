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
package org.apache.camel.component.vm;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;

/**
 * @version $Revision$
 */
public class VmInOutChainedTimeoutTest extends ContextTestSupport {

    public void testVmInOutChainedTimeout() throws Exception {
        // time timeout after 2 sec should trigger a immediately reply
        StopWatch watch = new StopWatch();
        try {
            template.requestBody("vm:a?timeout=5000", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
            assertEquals(2000, cause.getTimeout());
        }
        long delta = watch.stop();

        assertTrue("Should be faster than 3000 millis, was: " + delta, delta < 3000);

        Thread.sleep(2000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("vm:a")
                        .to("mock:a")
                        // this timeout will trigger an exception to occur
                        .to("vm:b?timeout=2000")
                        .to("mock:a2");

                from("vm:b")
                        .to("mock:b")
                        .delay(3000)
                        .transform().constant("Bye World");
            }
        };
    }
}