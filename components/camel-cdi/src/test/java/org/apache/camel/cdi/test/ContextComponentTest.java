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
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.DefaultCamelContextBean;
import org.apache.camel.cdi.bean.FirstNamedCamelContextBean;
import org.apache.camel.cdi.bean.FirstNamedCamelContextRoute;
import org.apache.camel.cdi.bean.SecondNamedCamelContextBean;
import org.apache.camel.cdi.bean.SecondNamedCamelContextRoute;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class ContextComponentTest {

    @Inject
    private CamelContext main;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(
                DefaultCamelContextBean.class,
                FirstNamedCamelContextBean.class,
                FirstNamedCamelContextRoute.class,
                SecondNamedCamelContextBean.class,
                SecondNamedCamelContextRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    @InSequence(1)
    public void addRouteToMainContext() throws Exception {
        main.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:inbound").to("first:in");
                from("first:out").to("second:in");
                from("second:out").to("mock:outbound");
            }
        });
    }

    @Test
    @InSequence(2)
    public void sendMessageToInbound(@Uri("direct:inbound") ProducerTemplate inbound,
                                     @Uri("mock:outbound") MockEndpoint outbound) throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.expectedBodiesReceived("second-first-test");

        inbound.sendBody("test");

        MockEndpoint.assertIsSatisfied(1L, TimeUnit.SECONDS, outbound);
    }
}
