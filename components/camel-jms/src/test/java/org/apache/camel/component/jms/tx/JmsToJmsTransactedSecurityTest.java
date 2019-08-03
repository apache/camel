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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JmsToJmsTransactedSecurityTest extends CamelSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsToJmsTransactedSecurityTest.class);
    
    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/jms/tx/JmsToJmsTransactedSecurityTest.xml");
    }
    
    @Test
    public void testJmsSecurityFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo")
                        .transacted()
                        .to("log:foo")
                        .to("activemq:queue:bar");

                from("activemq:queue:bar").to("mock:bar");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(0);

        template.sendBody("activemq:queue:foo", "Hello World");
        // get the message that got rolled back
        Exchange exch = consumer.receive("activemq:queue:foo", 250);
        if (exch != null) {
            LOG.info("Cleaned up orphaned message: " + exch);
        }
        mock.assertIsSatisfied(3000);
    }

    @Test
    public void testJmsSecurityOK() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("log:start")
                        .to("activemq:queue:foo");

                from("activemq:queue:foo").to("mock:foo");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
       
    }

}
