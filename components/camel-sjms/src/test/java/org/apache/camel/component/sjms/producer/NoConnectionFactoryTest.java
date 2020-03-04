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
package org.apache.camel.component.sjms.producer;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A unit test to ensure getting a meaningful error message
 * when neither of ConnectionResource nor ConnectionFactory is configured.
 */
public class NoConnectionFactoryTest {

    private static final Logger LOG = LoggerFactory.getLogger(NoConnectionFactoryTest.class);

    @Test
    public void testConsumerInOnly() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createConsumerInOnlyRouteBuilder());
        try {
            context.start();
        } catch (Throwable t) {
            Assert.assertEquals(IllegalArgumentException.class, t.getClass());
            LOG.info("Expected exception was thrown", t);
            return;
        }
        Assert.fail("No exception was thrown");
    }

    @Test
    public void testConsumerInOut() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createConsumerInOutRouteBuilder());
        try {
            context.start();
        } catch (Throwable t) {
            Assert.assertEquals(IllegalArgumentException.class, t.getClass());
            LOG.info("Expected exception was thrown", t);
            return;
        }
        Assert.fail("No exception was thrown");
    }

    @Test
    public void testProducerInOnly() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createProducerInOnlyRouteBuilder());
        try {
            context.start();
        } catch (Throwable t) {
            Assert.assertEquals(FailedToStartRouteException.class, t.getClass());
            Assert.assertEquals(IllegalArgumentException.class, t.getCause().getCause().getClass());
            LOG.info("Expected exception was thrown", t);
            return;
        }
        Assert.fail("No exception was thrown");
    }

    @Test
    public void testProducerInOut() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createProducerInOutRouteBuilder());
        try {
            context.start();
        } catch (Throwable t) {
            Assert.assertEquals(FailedToStartRouteException.class, t.getClass());
            Assert.assertEquals(IllegalArgumentException.class, t.getCause().getCause().getClass());
            LOG.info("Expected exception was thrown", t);
            return;
        }
        Assert.fail("No exception was thrown");
    }

    protected RouteBuilder createConsumerInOnlyRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms:queue:test-in?exchangePattern=InOnly")
                    .to("mock:result");
            }
        };
    }

    protected RouteBuilder createConsumerInOutRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms:queue:test-in?exchangePattern=InOut")
                    .to("mock:result");
            }
        };
    }

    protected RouteBuilder createProducerInOnlyRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:inonly")
                    .to("sjms:queue:test-out?exchangePattern=InOnly")
                    .to("mock:result");
            }
        };
    }

    protected RouteBuilder createProducerInOutRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:inout")
                    .to("sjms:queue:test-out?exchangePattern=InOut")
                    .to("mock:result");
            }
        };
    }
}
