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
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.component.resteasy.test.beans.Customer;
import org.apache.camel.component.resteasy.test.beans.ProxyBean;
import org.apache.camel.component.resteasy.test.beans.ProxyServiceInterface;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class ResteasyConsumerProxyTest {
    
    @ArquillianResource
    URI baseUri;
    
    @Deployment
    public static Archive<?> createTestArchive() {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war")
                .addClasses(ProxyServiceInterface.class, ProxyBean.class, Customer.class)
        .addPackage("org.apache.camel.component.resteasy")
        .addPackage("org.apache.camel.component.resteasy.servlet")
        .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml")
        .importRuntimeAndTestDependencies().resolve().withTransitivity().asFile())
        .addAsWebInfResource(new File("src/test/resources/contexts/consumerProxy.xml"), "applicationContext.xml")
        .addAsWebInfResource("web.xml");
        
        return war;
    }


    @Test
    public void testProxyOnlyFromCamel() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "camel/address");
        Response response = target.request().get();

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("Proxy address only from Camel", response.readEntity(String.class));
    }

    @Test
    public void testProxyFromInterface() throws Exception {
        Response response = ResteasyClientBuilder.newClient().target(baseUri.toString() + "proxy/get").request().get();

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("Address from ProxyInterface", response.readEntity(String.class));

    }

    // FIX THIS: using bean in camel route a getting body as Customer.class doesn't work -> need to investigate
    // RESOLUTION: resteasy-jackson2-provider test dependency resolves it.
    @Test
    public void testProxyPostFromInterface() throws Exception {
        Customer customer = new Customer("Camel", "Rider", 1);

        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "proxy/createCustomer");
        Response response = target.request(MediaType.APPLICATION_JSON).post(Entity.entity(customer, MediaType.APPLICATION_JSON_TYPE));

        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("Customer added : {\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":1}", response.readEntity(String.class));

    }

    @Test
    public void testWrongMethodOnProxyInterface() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "proxy/createCustomer");
        Response response = target.request().get();

        Assert.assertEquals(405, response.getStatus());

    }
}
