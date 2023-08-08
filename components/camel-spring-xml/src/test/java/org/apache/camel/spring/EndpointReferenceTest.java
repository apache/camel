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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spring.example.DummyBean;
import org.apache.camel.support.CamelContextHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EndpointReferenceTest extends SpringTestSupport {
    protected static Object body = "<hello>world!</hello>";

    @Test
    public void testContextToString() throws Exception {
        assertNotNull(context.toString());
    }

    @Test
    public void testEndpointConfiguration() throws Exception {
        Endpoint endpoint = getMandatoryBean(Endpoint.class, "endpoint1");

        assertEquals("direct://start", endpoint.getEndpointUri(), "endpoint URI");

        DummyBean dummyBean = getMandatoryBean(DummyBean.class, "mybean");
        assertNotNull(dummyBean.getEndpoint(), "The bean should have an endpoint injected");
        assertEquals("direct://start", dummyBean.getEndpoint().getEndpointUri(), "endpoint URI");

        log.debug("Found dummy bean: {}", dummyBean);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:end");
        resultEndpoint.expectedBodiesReceived(body);

        // now lets send a message
        template.sendBody("direct:start", body);

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected SpringCamelContext createCamelContext() {
        return applicationContext.getBean("camel", SpringCamelContext.class);
    }

    @Test
    public void testEndpointConfigurationAfterEnsuringThatTheStatementRouteBuilderWasCreated() throws Exception {
        String[] names = applicationContext.getBeanDefinitionNames();
        for (String name : names) {
            log.debug("Found bean name: {}", name);
        }

        testEndpointConfiguration();
    }

    @Test
    public void testReferenceEndpointFromOtherCamelContext() throws Exception {
        CamelContext context = applicationContext.getBean("camel2", CamelContext.class);
        RouteDefinition route = new RouteDefinition("temporary");
        String routeId = route.idOrCreate(context.getCamelContextExtension().getContextPlugin(NodeIdFactory.class));
        try {
            CamelContextHelper.resolveEndpoint(context, null, "endpoint1");
            fail("Should have thrown exception");
        } catch (NoSuchEndpointException exception) {
            assertTrue(exception.getMessage().contains("make sure the endpoint has the same camel context as the route does"),
                    "Get a wrong exception message");
        }
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/endpointReference.xml");
    }
}
