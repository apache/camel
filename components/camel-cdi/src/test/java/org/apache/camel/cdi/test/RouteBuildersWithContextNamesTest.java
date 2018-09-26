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
package org.apache.camel.cdi.test;

import java.util.concurrent.TimeUnit;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.FirstCamelContextEndpointInjectRoute;
import org.apache.camel.cdi.bean.SecondCamelContextEndpointInjectRoute;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class RouteBuildersWithContextNamesTest {

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(
                FirstCamelContextEndpointInjectRoute.class,
                SecondCamelContextEndpointInjectRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void verifyCamelContexts(@Any Instance<CamelContext> contexts) {
        assertThat("Context instances are incorrect!", contexts,
            containsInAnyOrder(
                hasProperty("name", is(equalTo("first"))),
                hasProperty("name", is(equalTo("second")))));
    }

    @Test
    public void verifyFirstCamelContext(@ContextName("first") CamelContext first) {
        assertThat("Context name is incorrect!", first.getName(), is(equalTo("first")));
        assertThat("Number of routes is incorrect!", first.getRoutes().size(), is(equalTo(1)));
        assertThat("Context status is incorrect!", first.getStatus(), is(equalTo(ServiceStatus.Started)));
    }

    @Test
    public void verifySecondCamelContext(@ContextName("second") CamelContext second) {
        assertThat("Context name is incorrect!", second.getName(), is(equalTo("second")));
        assertThat("Number of routes is incorrect!", second.getRoutes().size(), is(equalTo(1)));
        assertThat("Context status is incorrect!", second.getStatus(), is(equalTo(ServiceStatus.Started)));
    }

    @Test
    public void sendMessageToFirstInbound(@Uri(value = "direct:inbound") @ContextName("first")
                                          ProducerTemplate inbound,
                                          @Uri(value = "mock:outbound") @ContextName("first")
                                          MockEndpoint outbound) throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.expectedBodiesReceived("test");
        outbound.expectedHeaderReceived("context", "first");

        inbound.sendBody("test");

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);
    }

    @Test
    public void sendMessageToSecondInbound(@Uri("direct:inbound") @ContextName("second")
                                           ProducerTemplate inbound,
                                           @Uri("mock:outbound") @ContextName("second")
                                           MockEndpoint outbound) throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.expectedBodiesReceived("test");
        outbound.expectedHeaderReceived("context", "second");

        inbound.sendBody("test");

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);
    }
}
