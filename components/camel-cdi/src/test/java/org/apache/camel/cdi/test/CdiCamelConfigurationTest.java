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
package org.apache.camel.cdi.test;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelConfiguration;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.bean.EndpointInjectRoute;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class CdiCamelConfigurationTest {
    private static boolean configMethodHasBeenCalled;

    @Inject
    private CamelContext camelContext;

    @Deployment
    static Archive<?> createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(CdiCamelConfigurationTest.class)
            // RouteBuilder which should not appear in the context
            .addClass(EndpointInjectRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    static void configuration(@Observes CdiCamelConfiguration configuration) {
        configMethodHasBeenCalled = true;
        configuration.autoConfigureRoutes(false);
    }

    @Test
    public void checkThatConfigMethodHasBeenCalled() {
        assertTrue("Config method has not been called", configMethodHasBeenCalled);
    }

    @Test
    public void checkThatNoRouteBuildersAddedToContext() {
        assertTrue("There are RouteBuilder instances in context", camelContext.getRoutes().isEmpty());
    }
}
