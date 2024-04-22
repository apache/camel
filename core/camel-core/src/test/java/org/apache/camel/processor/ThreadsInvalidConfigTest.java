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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy.Abort;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ThreadsInvalidConfigTest extends ContextTestSupport {

    final ThreadPoolProfile threadPoolProfile = new ThreadPoolProfile("poll");

    @Test
    public void testCreateRouteIfNoInvalidOptions() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                from("direct:start").threads().executorService(threadPoolProfile.getId()).to("mock:test");
            }
        });
    }

    @Test
    public void testFailIfThreadNameAndExecutorServiceRef() {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                    from("direct:start").threads().executorService(threadPoolProfile.getId()).threadName("foo")
                            .to("mock:test");
                }
            });
        } catch (Exception e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertTrue(e.getCause().getMessage().startsWith("ThreadName"));
            return;
        }
        fail();
    }

    @Test
    public void testPassIfThreadNameWithoutExecutorServiceRef() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                from("direct:start").threads().threadName("foo").to("mock:test");
            }
        });
    }

    @Test
    public void testFailIfPoolSizeAndExecutorServiceRef() {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                    from("direct:start").threads().executorService(threadPoolProfile.getId()).poolSize(1).to("mock:test");
                }
            });
        } catch (Exception e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertTrue(e.getCause().getMessage().startsWith("PoolSize"));
            return;
        }
        fail();
    }

    @Test
    public void testFailIfMaxPoolSizeAndExecutorServiceRef() {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                    from("direct:start").threads().executorService(threadPoolProfile.getId()).maxPoolSize(1).to("mock:test");
                }
            });
        } catch (Exception e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertTrue(e.getCause().getMessage().startsWith("MaxPoolSize"));
            return;
        }
        fail();
    }

    @Test
    public void testFailIfKeepAliveTimeAndExecutorServiceRef() {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                    from("direct:start").threads().executorService(threadPoolProfile.getId()).keepAliveTime(1)
                            .to("mock:test");
                }
            });
        } catch (Exception e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertTrue(e.getCause().getMessage().startsWith("KeepAliveTime"));
            return;
        }
        fail();
    }

    @Test
    public void testFailIfMaxQueueSizeAndExecutorServiceRef() {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                    from("direct:start").threads().executorService(threadPoolProfile.getId()).maxQueueSize(1)
                            .to("mock:test");
                }
            });
        } catch (Exception e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertTrue(e.getCause().getMessage().startsWith("MaxQueueSize"));
            return;
        }
        fail();
    }

    @Test
    public void testFailIfRejectedPolicyAndExecutorServiceRef() {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    context.getExecutorServiceManager().registerThreadPoolProfile(threadPoolProfile);
                    from("direct:start").threads().executorService(threadPoolProfile.getId()).rejectedPolicy(Abort)
                            .to("mock:test");
                }
            });
        } catch (Exception e) {
            boolean b = e.getCause() instanceof IllegalArgumentException;
            assertTrue(b);
            assertTrue(e.getCause().getMessage().startsWith("RejectedPolicy"));
            return;
        }
        fail();
    }

}
