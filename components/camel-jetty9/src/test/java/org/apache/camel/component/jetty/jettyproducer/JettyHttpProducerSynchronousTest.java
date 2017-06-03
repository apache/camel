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
package org.apache.camel.component.jetty.jettyproducer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 * @version 
 */
public class JettyHttpProducerSynchronousTest extends BaseJettyTest {

    private static String beforeThreadName;
    private static String afterThreadName;
    private String url = "jetty://http://127.0.0.1:" + getPort() + "/sync?synchronous=true";

    @Test
    public void testSynchronous() throws Exception {
        
        // give Jetty time to startup properly
        Thread.sleep(1000);

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();

        assertTrue("Should use same threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("log:before")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            beforeThreadName = Thread.currentThread().getName();
                        }
                    })
                    .to(url)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            afterThreadName = Thread.currentThread().getName();
                        }
                    })
                    .to("log:after")
                    .to("mock:result");

                from(url).transform(constant("Bye World"));
            }
        };
    }
}