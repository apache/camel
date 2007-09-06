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

import java.util.List;

import org.apache.camel.CamelTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.example.DummyBean;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: 521586 $
 */
public class EndpointReferenceTest extends SpringTestSupport {
    protected static Object body = "<hello>world!</hello>";

    public void testEndpointConfiguration() throws Exception {
        Endpoint endpoint = getMandatoryBean(Endpoint.class, "endpoint1");

        assertEquals("endpoint URI", "direct:start", endpoint.getEndpointUri());

        DummyBean dummyBean = getMandatoryBean(DummyBean.class, "mybean");
        assertNotNull("The bean should have an endpoint injected", dummyBean.getEndpoint());
        assertEquals("endpoint URI", "direct:start", dummyBean.getEndpoint().getEndpointUri());

        log.debug("Found dummy bean: " + dummyBean);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:end");
        resultEndpoint.expectedBodiesReceived(body);

        // now lets send a message
        template.sendBody("direct:start", body);

        resultEndpoint.assertIsSatisfied();
    }

    protected SpringCamelContext createCamelContext() {
        return (SpringCamelContext) applicationContext.getBean("camel");
    }

    public void testEndpointConfigurationAfterEnsuringThatTheStatementRouteBuilderWasCreated() throws Exception {
        String[] names = applicationContext.getBeanDefinitionNames();
        for (String name : names) {
            log.debug("Found bean name: " + name);
        }

        testEndpointConfiguration();
    }

    protected void assertValidContext(SpringCamelContext context) {
        super.assertValidContext(context);

        List<Route> routes = context.getRoutes();
        assertEquals("Number of routes defined", 1, routes.size());
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/endpointReference.xml");
    }
}
