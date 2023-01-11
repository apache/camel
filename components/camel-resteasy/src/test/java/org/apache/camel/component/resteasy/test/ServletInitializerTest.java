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

import jakarta.ws.rs.core.Response;

import org.apache.camel.component.resteasy.test.WebTest.Deployment;
import org.apache.camel.component.resteasy.test.WebTest.Resource;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * See the Servlet 3.0 spec, section 8.2.4 for implementation and processing the details of ServletContainerInitializer.
 * 
 * Resteasy's implementation of ServletContainerInitializer is declared in the META-INF/services directory of archive
 * org.jboss.resteasy:resteasy-servlet-initializer as required by the spec. This archive MUST be included in the
 * generated WAR file so the server can find and call it. Shrinkwrap's Maven class and .addAsLibraries method is used to
 * achieve this.
 * 
 * This test checks that the implementation properly handles a jaxrs app that provides resource and provider classes as
 * well as no web.xml file.
 */

@WebTest
public class ServletInitializerTest {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {
        File pomFile = Maven.resolver().loadPomFromFile("pom.xml").resolve("org.jboss.resteasy:resteasy-servlet-initializer")
                .withoutTransitivity().asSingleFile();

        WebArchive war = ShrinkWrap.create(WebArchive.class, "RESTEASY-1630-two.war")
                .addClasses(TestApplication.class)
                .addClasses(TestResource.class)
                .addAsLibraries(pomFile)
                .addAsWebInfResource(new File("src/test/resources/web-no-spring.xml"), "web.xml");
        return war;
    }

    /**
     * App declares files via the web.xml
     *
     */
    @Test
    public void testEndpoint() {
        Response response = ResteasyClientBuilder.newClient()
                .target(baseUri.toString() + "test/17").request().get();
        String entity = response.readEntity(String.class);
        assertEquals(200, response.getStatus());
        assertEquals("17", entity);
    }
}
