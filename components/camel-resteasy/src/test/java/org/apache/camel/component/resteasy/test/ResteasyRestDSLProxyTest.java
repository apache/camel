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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.camel.component.resteasy.test.WebTest.Deployment;
import org.apache.camel.component.resteasy.test.WebTest.Resource;
import org.apache.camel.component.resteasy.test.beans.Customer;
import org.apache.camel.component.resteasy.test.beans.ProxyServiceInterface;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WebTest
public class ResteasyRestDSLProxyTest {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war")
                .addClasses(ProxyServiceInterface.class, Customer.class)
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeAndTestDependencies().resolve()
                        .withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/contexts/restDSLProxy.xml"),
                        "applicationContext.xml")
                .addAsWebInfResource("web.xml");
        return war;
    }

    @Test
    public void testRestDSLProxy() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "proxy/get");
        Response response = target.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("Address from ProxyInterface", response.readEntity(String.class));

    }
}
