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
package org.apache.camel.spring.config;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RouteRefPropertyPlaceholderMultipleCamelContextRefsTest extends Assert {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/RouteRefPropertyPlaceholderMultipleCamelContextRefsTest.xml");
    }

    @Test
    public void testSpringTwoCamelContextDirectEndpoint() throws Exception {
        AbstractXmlApplicationContext ac = createApplicationContext();
        ac.start();

        CamelContext camel1 = ac.getBean("myCamel-1", CamelContext.class);
        CamelContext camel2 = ac.getBean("myCamel-2", CamelContext.class);

        Endpoint start1 = camel1.getEndpoint("direct:start");
        Endpoint start2 = camel2.getEndpoint("direct:start");
        assertNotSame(start1, start2);
        
        MockEndpoint mock1 = camel1.getEndpoint("mock:end-1", MockEndpoint.class);
        mock1.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = camel2.getEndpoint("mock:end-2", MockEndpoint.class);
        mock2.expectedBodiesReceived("Bye World");

        camel1.createProducerTemplate().sendBody("direct:start", "Hello World");
        camel2.createProducerTemplate().sendBody("direct:start", "Bye World");

        mock1.assertIsSatisfied();
        mock2.assertIsSatisfied();

        ac.stop();
    }
    
}