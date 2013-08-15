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
package org.apache.camel.example.cdi.one;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Mock;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.cdi.MyRoutes;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// TODO: This should be refactored, to unit test the MyRoutes from the src/main/java
// we should not add new routes and whatnot. This is an example for end-users to use as best practice
// so we should show them how to unit test their routes from their main source code

@RunWith(Arquillian.class)
public class IntegrationTest extends DeploymentFactory {

    static boolean routeConfigured;
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTest.class);

    @Inject
    MyRoutes config;

    @Inject
    @Mock
    MockEndpoint result;

    @Produces
    @ApplicationScoped
    @ContextName
    public RouteBuilder createRoute() {
        return new RouteBuilder() {
            public void configure() {
                routeConfigured = true;
                Endpoint resultEndpoint = config.getResultEndpoint();
                LOG.info("consuming from output: " + resultEndpoint + " to endpoint: " + result);

                from(resultEndpoint).to(result);
            }
        };
    }

    @Test
    @Ignore("Does not work")
    public void integrationTest() throws Exception {
        assertNotNull("config not injected!", config);
        assertNotNull("MockEndpoint result not injected!", result);
        assertTrue("RouteBuilder has not been configured!", routeConfigured);

        result.expectedMessageCount(2);
        result.assertIsSatisfied();
    }

}
