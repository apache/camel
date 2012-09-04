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
package org.apache.camel.example.cdi;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Mock;
import org.apache.camel.component.cdi.internal.CamelExtension;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 */
@RunWith(Arquillian.class)
public class IntegrationTest {

    @Inject
    MyRoutes config;

    @Inject
    CamelContext camelContext;

    @Inject
    @Mock
    MockEndpoint result;

    @Produces
    public RouteBuilder createRoutes() {
        return new RouteBuilder() {
            public void configure() {
                from(config.getResultEndpoint()).to(result);
            }
        };
    }

    @Test
    public void integrationTest() throws Exception {
        assertNotNull("CamelContext not injected!", camelContext);
        assertTrue("CamelContext is started", camelContext.getStatus().isStarted());

        assertNotNull("config not injected!", config);
        assertNotNull("MockEndpoint result not injected!", result);

        //camelContext.setTracing(true);

        // TODO we could maybe auto-register this?
        camelContext.addRoutes(createRoutes());

        result.expectedMessageCount(2);
        result.assertIsSatisfied();
    }

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addPackage(CamelExtension.class.getPackage())
                .addPackage(MyRoutes.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }
}
