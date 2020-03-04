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
package org.apache.camel.component.sjms.tx;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.CamelJmsTestHelper;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A unit test to ensure the error is raised against incompatible configuration, InOut + transacted.
 */
public class TransactedProducerInOutErrorTest {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedProducerInOutErrorTest.class);

    @Test(expected = FailedToStartRouteException.class)
    public void test() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        SjmsComponent component = context.getComponent("sjms", SjmsComponent.class);
        component.setConnectionFactory(CamelJmsTestHelper.createConnectionFactory());
        try {
            context.start();
        } catch (Throwable t) {
            Assert.assertEquals(FailedToStartRouteException.class, t.getClass());
            Assert.assertEquals(IllegalArgumentException.class, t.getCause().getCause().getClass());
            LOG.info("Exception was thrown as expected", t);
            throw t;
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start")
                    .to("sjms:queue:test-in?namedReplyTo=test-out&exchangePattern=InOut&transacted=true")
                    .to("mock:result");

                from("sjms:queue:test-in?exchangePattern=InOut")
                    .log("Using ${threadName} to process ${body}")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
