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
package org.apache.camel.spring.boot;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultShutdownStrategy;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = CustomShutdownStrategyTest.class)
public class CustomShutdownStrategyTest extends Assert {

    @Autowired
    CamelContext camelContext;

    @Configuration
    @EnableAutoConfiguration
    public static class Config extends SpringRouteBuilder {

        @Override
        public void configure() throws Exception {
            from("seda:foo").to("mock:foo");
        }

        @Bean
        public ShutdownStrategy shutdownStrategy() {
            ShutdownStrategy strat = new MyShutdown();
            strat.setTimeout(60000);
            return strat;
        }

    }

    @Test
    public void testCustomShutdown() throws Exception {
        ShutdownStrategy stat = camelContext.getShutdownStrategy();
        assertEquals(60000, stat.getTimeout());
        assertTrue(stat instanceof MyShutdown);

        camelContext.stop();

        assertTrue(((MyShutdown)stat).isInvoked());
    }

    private static class MyShutdown extends DefaultShutdownStrategy {

        private boolean invoked;

        @Override
        protected boolean doShutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit,
                                     boolean suspendOnly, boolean abortAfterTimeout, boolean forceShutdown) throws Exception {
            invoked = true;
            return super.doShutdown(context, routes, timeout, timeUnit, suspendOnly, abortAfterTimeout, forceShutdown);
        }

        boolean isInvoked() {
            return invoked;
        }
    }

}

