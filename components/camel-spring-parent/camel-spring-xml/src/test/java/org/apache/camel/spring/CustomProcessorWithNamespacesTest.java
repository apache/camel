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
package org.apache.camel.spring;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.spring.example.MyProcessor;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomProcessorWithNamespacesTest extends TestSupport {
    protected String body = "<hello>world!</hello>";
    protected AbstractXmlApplicationContext applicationContext;

    @Test
    public void testXMLRouteLoading() throws Exception {
        applicationContext = createApplicationContext();

        SpringCamelContext context = applicationContext.getBeansOfType(SpringCamelContext.class).values().iterator().next();
        assertValidContext(context);

        // now lets send a message
        ProducerTemplate template = context.createProducerTemplate();
        template.start();
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setHeader("name", "James");
                in.setBody(body);
            }
        });
        template.stop();

        MyProcessor myProcessor = applicationContext.getBean("myProcessor", MyProcessor.class);
        List<Exchange> list = myProcessor.getExchanges();
        assertEquals(1, list.size(), "Should have received a single exchange: " + list);
    }

    protected void assertValidContext(SpringCamelContext context) {
        assertNotNull(context, "No context found!");

        List<Route> routes = context.getRoutes();
        assertNotNull(routes, "Should have some routes defined");
        assertEquals(1, routes.size(), "Number of routes defined");
        Route route = routes.get(0);
        log.debug("Found route: {}", route);
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/routingUsingProcessor.xml");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        IOHelper.close(applicationContext);
    }
}
