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
import java.nio.file.Files;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.apache.camel.component.resteasy.test.WebTest.Deployment;
import org.apache.camel.component.resteasy.test.WebTest.Resource;
import org.apache.camel.component.resteasy.test.beans.SimpleService;
import org.apache.camel.component.resteasy.test.beans.TestBean;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WebTest
public class ResteasySimpleConsumerTest {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {

        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addClasses(SimpleService.class, TestBean.class)
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeAndTestDependencies().resolve()
                        .withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/contexts/simpleConsumer.xml"), "applicationContext.xml")
                .addAsWebInfResource("web.xml");
    }

    @Test
    public void testGettingResponseFromBean() {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "simpleService/getMsg");
        Response response = target.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("Added this message from bean to original message from Rest Service -> Message1 from Rest service",
                response.readEntity(String.class));
    }

    @Test
    public void testGettingBodyFromCamelRoute() {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "simpleService/getMsg2");
        Response response = target.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("Overriding output from Rest service", response.readEntity(String.class));
    }

    @Test
    public void testGettingResponseFromRestService() throws Exception {
        String expectedResponse = "Message3 from Rest service";

        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "simpleService/getMsg3");
        Response response = target.request().get();

        assertEquals(200, response.getStatus());
        assertEquals(expectedResponse, response.readEntity(String.class));

        File file = new File("target/messageTest/response.txt");
        byte[] encoded = Files.readAllBytes(file.toPath());
        String responseBody = new String(encoded);

        assertEquals(expectedResponse, responseBody);
    }

}
