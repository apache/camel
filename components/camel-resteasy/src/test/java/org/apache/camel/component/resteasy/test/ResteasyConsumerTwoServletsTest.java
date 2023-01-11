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
package org.apache.camel.component.resteasy.test;

import java.io.File;
import java.net.URI;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.apache.camel.component.resteasy.test.WebTest.Deployment;
import org.apache.camel.component.resteasy.test.WebTest.Resource;
import org.apache.camel.component.resteasy.test.beans.ServletOneService;
import org.apache.camel.component.resteasy.test.beans.ServletTwoService;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WebTest
public class ResteasyConsumerTwoServletsTest {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {

        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addClasses(ServletOneService.class, ServletTwoService.class)
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml")
                        .importRuntimeAndTestDependencies().resolve().withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/contexts/twoServlets.xml"), "applicationContext.xml")
                .addAsWebInfResource(new File("src/test/resources/webTwoServlets.xml"), "web.xml");
    }

    @Test
    public void testServletNameOption() {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "numberOne/simpleServiceSecure/getMsg");
        Response response = target.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("Message from camel-servlet-1", response.readEntity(String.class));

        Client client2 = ClientBuilder.newBuilder().build();
        WebTarget target2 = client2.target(baseUri.toString() + "numberTwo/simpleServiceSecure/getMsg");
        Response response2 = target2.request().get();

        assertEquals(200, response2.getStatus());
        assertEquals("Message from camel-servlet-2", response2.readEntity(String.class));
    }
}
