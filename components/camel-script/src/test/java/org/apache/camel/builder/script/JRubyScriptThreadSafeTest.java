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
package org.apache.camel.builder.script;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JRubyScriptThreadSafeTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint resultEndpoint;
    @EndpointInject(uri = "mock:error")
    MockEndpoint errorEndpoint;
    
    final int messageCount = 200;
    final CountDownLatch latch = new CountDownLatch(messageCount);
    long start;

    @Before
    public void setUp() throws Exception {
        setUpEnv();
        super.setUp();
    }

    protected void setUpEnv() {
        System.setProperty("org.jruby.embed.localcontext.scope", "threadsafe");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                context.getGlobalOptions().put(Exchange.REUSE_SCRIPT_ENGINE, "true");
                // Now the default the value is not use the compiled script, as it could introduce some concurrent issue
                context.getGlobalOptions().put(Exchange.COMPILE_SCRIPT, "false");


                from("seda:parallel?concurrentConsumers=5")
                    .onException(Exception.class)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                Throwable ex = exchange.getProperty("CamelExceptionCaught", Throwable.class);
                                System.out.println(ex);
                                ex.printStackTrace();
                                latch.countDown();
                            }
                        })
                        .to(errorEndpoint)
                    .end()
                    .to("language:ruby:result = $request.body?cacheScript=true")
                    .to(resultEndpoint)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            latch.countDown();
                        }
                    });

                from("seda:sequential?concurrentConsumers=1")
                    .onException(Exception.class)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                latch.countDown();
                            }
                        })
                        .to(errorEndpoint)
                    .end()
                    .to("language:ruby:result = $request.body?cacheScript=true")
                    .to(resultEndpoint)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            latch.countDown();
                        }
                    });
            }
        };
    }

    @Test
    public void testParallelLocalContext() throws Exception {

        resultEndpoint.setExpectedMessageCount(messageCount);
        startStopWatch();
        for (int i = 1; i < messageCount + 1; i++) {
            template.sendBody("seda:parallel", "BODY" + i);
        }
        latch.await();
        stopStopWatch();
        assertMockEndpointsSatisfied(2, TimeUnit.SECONDS);
        checkResult();
        
    }

    @Test
    public void testSequentialLocalContext() throws Exception {
        resultEndpoint.setExpectedMessageCount(messageCount);
        startStopWatch();
        for (int i = 1; i < messageCount + 1; i++) {
            template.sendBody("seda:sequential", "BODY" + i);
        }
        latch.await();
        stopStopWatch();
        assertMockEndpointsSatisfied(2, TimeUnit.SECONDS);
        checkResult();
        
    }

    private void checkResult() {
        Set<String> bodies = new HashSet<String>();
        for (Exchange exchange : resultEndpoint.getReceivedExchanges()) {
            bodies.add(exchange.getIn().getBody(String.class));
        }
        Assert.assertEquals("duplicate bodies:", messageCount, bodies.size());
    }

    private void startStopWatch() {
        start = System.currentTimeMillis();
    }

    private void stopStopWatch() {
        System.out.println(this.getTestMethodName() + " processing time: " + (System.currentTimeMillis() - start) + "ms.");
    }

}

