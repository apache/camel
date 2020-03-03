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
package org.apache.camel.component.bean;

import org.apache.camel.BeanScope;
import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.jndi.JndiBeanRepository;
import org.apache.camel.support.jndi.JndiContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewInstanceTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected JndiContext jndiContext;

    @Test
    public void testSendMessageToDifferentBeans() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceived(1, 2);

        template.sendBody("direct:start", ExchangePattern.InOut, "first");

        // lets simulate spring's factory bean stuff
        jndiContext.unbind("myBean");
        jndiContext.bind("myBean", new MyBean());

        template.sendBody("direct:start", ExchangePattern.InOut, "second");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        jndiContext = new JndiContext();
        jndiContext.bind("myBean", new MyBean());
        return new DefaultRegistry(new JndiBeanRepository(jndiContext));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").bean("myBean", BeanScope.Prototype).to("mock:result");
            }
        };
    }

    public static class MyBean {
        private static int counter;
        private int id;

        public MyBean() {
            id = generateId();
        }

        protected static synchronized int generateId() {
            return ++counter;
        }

        @Override
        public String toString() {
            return "MyBean[" + id + "]";
        }

        public int read(@Body String body) {
            LOG.info("read() method called with: {} on {}", body, this);
            return id;
        }
    }
}
