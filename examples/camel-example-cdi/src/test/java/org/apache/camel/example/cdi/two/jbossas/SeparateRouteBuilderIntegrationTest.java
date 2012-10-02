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
package org.apache.camel.example.cdi.two.jbossas;

import org.apache.camel.cdi.CdiCamelContext;
import org.apache.camel.cdi.internal.CamelExtension;
import org.apache.camel.example.cdi.MyRoutes;
import org.apache.camel.example.cdi.two.TestRouteBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import java.io.File;

import static org.junit.Assert.assertNotNull;

/**
 * Lets use a separate {@link org.apache.camel.example.cdi.two.TestRouteBuilder} to test the routes
 */
@RunWith(Arquillian.class)
public class SeparateRouteBuilderIntegrationTest {
    @Inject
    TestRouteBuilder testRouteBuilder;

    @Test
    public void integrationTest() throws Exception {
        assertNotNull("testRouteBuilder not injected!", testRouteBuilder);

        testRouteBuilder.assertIsSatisfied();
    }

    @Deployment
    @TargetsContainer("jbossas-managed")
    public static Archive<?> createTestArchive() {


        JavaArchive jarTest = ShrinkWrap.create(JavaArchive.class)
                .addPackage(MyRoutes.class.getPackage())
                .addPackage(TestRouteBuilder.class.getPackage())
                .addPackage(SeparateRouteBuilderIntegrationTest.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

        File[] libs = Maven.resolver()
                .loadPomFromFile("pom.xml")
                .resolve("org.apache.camel:camel-core","org.apache.camel:camel-cdi","org.apache.activemq:activemq-camel")
                .withTransitivity()
                .as(File.class);

        return ShrinkWrap
                .create(WebArchive.class, "test.war")
                .addAsLibrary(jarTest)
                .addAsLibraries(libs);
    }
}
