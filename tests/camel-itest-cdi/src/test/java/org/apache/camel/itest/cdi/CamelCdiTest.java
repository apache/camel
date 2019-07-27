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
package org.apache.camel.itest.cdi;

import java.util.Map;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class CamelCdiTest {

    private static final Logger LOG = LoggerFactory.getLogger(CamelCdiTest.class);

    @Any
    @Inject
    Instance<CamelContext> camelContexts;
    
    @Inject
    @ContextName("contextA")
    RoutesContextA routesA;

    @Deployment
    public static JavaArchive createDeployment() {
        return Maven.configureResolver().workOffline()
            .loadPomFromFile("pom.xml")
            .resolve("org.apache.camel:camel-cdi")
            .withoutTransitivity()
            .asSingle(JavaArchive.class)
            .addClasses(
                RoutesContextA.class
            );
    }

    @Test
    public void checkContextsHaveCorrectEndpointsAndRoutes() throws Exception {
        assertNotNull("camelContexts not injected!", camelContexts);

        for (CamelContext camelContext : camelContexts) {
            LOG.info("CamelContext " + camelContext + " has endpoints: " + camelContext.getEndpointMap().keySet());
            camelContext.start();
        }

        CamelContext contextA = assertCamelContext("contextA");
        assertHasEndpoints(contextA, "seda://A.a", "mock://A.b");

        MockEndpoint mockEndpoint = routesA.b;
        mockEndpoint.expectedBodiesReceived(Constants.EXPECTED_BODIES_A);
        routesA.sendMessages();
        mockEndpoint.assertIsSatisfied();
    }

    public static void assertHasEndpoints(CamelContext context, String... uris) {
        Map<String, Endpoint> endpointMap = context.getEndpointMap();
        for (String uri : uris) {
            Endpoint endpoint = endpointMap.get(uri);
            assertNotNull("CamelContext " + context + " does not have an Endpoint with URI " + uri + " but has " + endpointMap.keySet(), endpoint);
        }
    }

    protected CamelContext assertCamelContext(String contextName) {
        CamelContext answer = camelContexts.select(ContextName.Literal.of(contextName)).get();
        assertTrue("CamelContext '" + contextName + "' is not started", answer.getStatus().isStarted());
        return answer;
    }
}
