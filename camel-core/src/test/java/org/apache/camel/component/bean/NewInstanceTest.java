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
package org.apache.camel.component.bean;

import javax.naming.Context;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class NewInstanceTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected JndiContext jndiContext;

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
    protected Context createJndiContext() throws Exception {
        jndiContext = new JndiContext();
        jndiContext.bind("myBean", new MyBean());
        return jndiContext;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").bean("myBean", false).to("mock:result");
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
