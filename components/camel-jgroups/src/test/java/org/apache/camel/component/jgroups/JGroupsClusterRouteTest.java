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
package org.apache.camel.component.jgroups;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.jgroups.JGroupsExpressions.delayIfContextNotStarted;
import static org.apache.camel.component.jgroups.JGroupsFilters.dropNonCoordinatorViews;

public class JGroupsClusterRouteTest extends Assert {

    // Routing fixtures

    CamelContext firstCamelContext;

    CamelContext secondCamelContext;

    String clusterName = randomUUID().toString();

    String masterMockUri = "mock:master?resultWaitTime=2m";

    class Builder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("jgroups:" + clusterName + "?enableViewMessages=true").
                    filter(dropNonCoordinatorViews()).
                    threads().delay(delayIfContextNotStarted(SECONDS.toMillis(15))).
                    to("controlbus:route?routeId=masterRoute&action=start&async=true");

            from("timer://master?repeatCount=1").routeId("masterRoute").autoStartup(false).to(masterMockUri);
        }
    }

    @Before
    public void setUp() throws Exception {
        firstCamelContext = new DefaultCamelContext();
        firstCamelContext.addRoutes(new Builder());

        secondCamelContext = new DefaultCamelContext();
        secondCamelContext.addRoutes(new Builder());
    }

    // Tests

    @Test
    public void shouldElectSecondNode() throws Exception {
        expectMasterIs(firstCamelContext);
        firstCamelContext.start();
        assertMasterIs(firstCamelContext);

        expectMasterIsNot(secondCamelContext);
        secondCamelContext.start();
        assertMasterIsNot(secondCamelContext);

        expectMasterIs(secondCamelContext);
        firstCamelContext.stop();
        assertMasterIs(secondCamelContext);
    }

    @Test
    public void shouldKeepMaster() throws Exception {
        expectMasterIs(firstCamelContext);
        firstCamelContext.start();
        assertMasterIs(firstCamelContext);

        expectMasterIsNot(secondCamelContext);
        secondCamelContext.start();
        assertMasterIsNot(secondCamelContext);

        expectMasterIs(firstCamelContext);
        secondCamelContext.stop();
        assertMasterIs(firstCamelContext);
    }

    @Test
    public void shouldElectSecondNodeAndReturnToFirst() throws Exception {
        expectMasterIs(firstCamelContext);
        firstCamelContext.start();
        assertMasterIs(firstCamelContext);

        expectMasterIsNot(secondCamelContext);
        secondCamelContext.start();
        assertMasterIsNot(secondCamelContext);

        expectMasterIsNot(firstCamelContext);
        firstCamelContext.stop();
        assertMasterIsNot(firstCamelContext);

        expectMasterIsNot(firstCamelContext);
        firstCamelContext.start();
        assertMasterIsNot(firstCamelContext);

        expectMasterIs(firstCamelContext);
        secondCamelContext.stop();
        assertMasterIs(firstCamelContext);
    }

    // Helpers

    private void expectMasterIs(CamelContext camelContext) {
        camelContext.getEndpoint(masterMockUri, MockEndpoint.class).expectedMessageCount(1);
    }

    private void expectMasterIsNot(CamelContext camelContext) {
        camelContext.getEndpoint(masterMockUri, MockEndpoint.class).expectedMessageCount(0);
    }

    private void assertMasterIs(CamelContext camelContext) throws InterruptedException {
        camelContext.getEndpoint(masterMockUri, MockEndpoint.class).assertIsSatisfied();
    }

    private void assertMasterIsNot(CamelContext camelContext) throws InterruptedException {
        camelContext.getEndpoint(masterMockUri, MockEndpoint.class).assertIsSatisfied();
    }

}