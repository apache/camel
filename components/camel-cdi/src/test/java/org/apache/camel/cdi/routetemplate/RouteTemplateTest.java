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
package org.apache.camel.cdi.routetemplate;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.DefaultCamelContextBean;
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

@RunWith(Arquillian.class)
public class RouteTemplateTest {

    @Inject
    private DefaultCamelContextBean defaultCamelContext;

    @Inject
    @Uri("direct:start")
    private ProducerTemplate defaultInbound;

    @Inject
    @Uri("mock:result")
    private MockEndpoint defaultOutbound;

    @Inject
    @Uri("direct:start2")
    private ProducerTemplate defaultInbound2;

    @Inject
    @Uri("mock:result2")
    private MockEndpoint defaultOutbound2;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
                // Camel CDI
                .addPackage(CdiCamelExtension.class.getPackage())
                // Test classes
                .addClasses(DefaultCamelContextBean.class, MyRouteCreatorBean.class, MyRoute.class, MySecondRoute.class,
                        MyTemplateRoute.class)
                // Bean archive deployment descriptor
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void verifyCamelContext() throws Exception {
        defaultOutbound.expectedBodiesReceived("Hello AB");
        defaultOutbound2.expectedBodiesReceived("Bye BA");

        defaultInbound.sendBody("direct:start", "Hello ");
        defaultInbound2.sendBody("direct:start2", "Bye ");

        assertIsSatisfied(2L, TimeUnit.SECONDS, defaultOutbound, defaultOutbound2);
    }

}
