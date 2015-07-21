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
package org.apache.camel.itest.cdi;

import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.internal.CamelContextMap;
import org.apache.camel.cdi.internal.CamelExtension;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.deltaspike.core.impl.scope.conversation.ConversationBeanHolder;
import org.apache.deltaspike.core.impl.scope.viewaccess.ViewAccessBeanHolder;
import org.apache.deltaspike.core.impl.scope.window.WindowBeanHolder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class CamelCdiTest {
    private static final Logger LOG = LoggerFactory.getLogger(CamelCdiTest.class);

    @Inject
    CamelContextMap camelContextMap;
    @Inject
    RoutesContextA routesA;
    @Inject
    RoutesContextB routesB;
    @Inject
    RoutesContextC routesC;
    @Inject
    RoutesContextD routesD;

    @Inject @Uri(value = "seda:foo", context = "contextD")
    ProducerTemplate producerD;

    @Test
    public void checkContextsHaveCorrectEndpointsAndRoutes() throws Exception {
        Set<Map.Entry<String, CamelContext>> entries = camelContextMap.getCamelContextMap().entrySet();
        for (Map.Entry<String, CamelContext> entry : entries) {
            LOG.info("CamelContext " + entry.getKey() + " has endpoints: " + entry.getValue().getEndpointMap().keySet());
        }

        CamelContext contextA = assertCamelContext("contextA");
        assertHasEndpoints(contextA, "seda://A.a", "mock://A.b");

        MockEndpoint mockEndpoint = routesA.b;
        mockEndpoint.expectedBodiesReceived(Constants.EXPECTED_BODIES_A);
        routesA.sendMessages();
        mockEndpoint.assertIsSatisfied();

        CamelContext contextB = assertCamelContext("contextB");
        assertHasEndpoints(contextB, "seda://B.a", "mock://B.b");

        MockEndpoint mockEndpointB = routesB.b;
        mockEndpointB.expectedBodiesReceived(Constants.EXPECTED_BODIES_B);
        routesB.sendMessages();
        mockEndpointB.assertIsSatisfied();

        // lets check the routes where we default the context from the @ContextName
        CamelContext contextC = assertCamelContext("contextC");
        assertHasEndpoints(contextC, "seda://C.a", "mock://C.b");

        MockEndpoint mockEndpointC = routesC.b;
        mockEndpointC.expectedBodiesReceived(Constants.EXPECTED_BODIES_C);
        routesC.sendMessages();
        mockEndpointC.assertIsSatisfied();

        CamelContext contextD = assertCamelContext("contextD");
        assertHasEndpoints(contextD, "seda://D.a", "mock://D.b");

        MockEndpoint mockEndpointD = routesD.b;
        mockEndpointD.expectedBodiesReceived(Constants.EXPECTED_BODIES_D);
        routesD.sendMessages();
        mockEndpointD.assertIsSatisfied();

        CamelContext contextE = assertCamelContext("contextD");
        assertHasEndpoints(contextE, "seda://D.a", "mock://D.b");
        MockEndpoint mockDb = CamelContextHelper.getMandatoryEndpoint(contextE, "mock://D.b", MockEndpoint.class);
        mockDb.reset();
        mockDb.expectedBodiesReceived(Constants.EXPECTED_BODIES_D_A);
        for (Object body : Constants.EXPECTED_BODIES_D_A) {
            producerD.sendBody("seda:D.a", body);
        }
        mockDb.assertIsSatisfied();
    }

    public static void assertHasEndpoints(CamelContext context, String... uris) {
        Map<String, Endpoint> endpointMap = context.getEndpointMap();
        for (String uri : uris) {
            Endpoint endpoint = endpointMap.get(uri);
            assertNotNull("CamelContext " + context + " does not have an Endpoint with URI " + uri + " but has " + endpointMap.keySet(), endpoint);
        }
    }

    protected CamelContext assertCamelContext(String contextName) {
        assertNotNull("camelContextMap not injected!", camelContextMap);
        CamelContext answer = camelContextMap.getMandatoryCamelContext(contextName);
        assertTrue("CamelContext '" + contextName + "' is not started", answer.getStatus().isStarted());
        return answer;
    }

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addPackage(CamelExtension.class.getPackage())
                .addPackage(RoutesContextA.class.getPackage())
                // add a bunch of deltaspike packages so we can find those cdi beans to make arquillian happy
                .addPackage(WindowBeanHolder.class.getPackage())
                .addPackage(ConversationBeanHolder.class.getPackage())
                .addPackage(ViewAccessBeanHolder.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }
}
