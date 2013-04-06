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
package org.apache.camel.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spring.example.DummyBean;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class EndpointReferenceTest extends SpringTestSupport {
    protected static Object body = "<hello>world!</hello>";

    public void testContextToString() throws Exception {
        assertNotNull(context.toString());
    }

    public void testEndpointConfiguration() throws Exception {
        Endpoint endpoint = getMandatoryBean(Endpoint.class, "endpoint1");

        assertEquals("endpoint URI", "direct://start", endpoint.getEndpointUri());

        DummyBean dummyBean = getMandatoryBean(DummyBean.class, "mybean");
        assertNotNull("The bean should have an endpoint injected", dummyBean.getEndpoint());
        assertEquals("endpoint URI", "direct://start", dummyBean.getEndpoint().getEndpointUri());

        log.debug("Found dummy bean: " + dummyBean);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:end");
        resultEndpoint.expectedBodiesReceived(body);

        // now lets send a message
        template.sendBody("direct:start", body);

        resultEndpoint.assertIsSatisfied();
    }

    protected SpringCamelContext createCamelContext() {
        return applicationContext.getBean("camel", SpringCamelContext.class);
    }

    public void testEndpointConfigurationAfterEnsuringThatTheStatementRouteBuilderWasCreated() throws Exception {
        String[] names = applicationContext.getBeanDefinitionNames();
        for (String name : names) {
            log.debug("Found bean name: " + name);
        }

        testEndpointConfiguration();
    }
    
    public void testReferenceEndpointFromOtherCamelContext() throws Exception {
        CamelContext context = applicationContext.getBean("camel2", CamelContext.class);
        RouteContext routeContext = new DefaultRouteContext(context);
        try {
            routeContext.resolveEndpoint(null, "endpoint1");
            fail("Should have thrown exception");
        } catch (NoSuchEndpointException exception) {
            assertTrue("Get a wrong exception message", exception.getMessage().contains("make sure the endpoint has the same camel context as the route does"));
        }
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/endpointReference.xml");
    }
}
