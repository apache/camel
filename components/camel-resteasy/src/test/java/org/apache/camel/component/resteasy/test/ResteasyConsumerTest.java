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
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.camel.component.resteasy.test.WebTest.Deployment;
import org.apache.camel.component.resteasy.test.WebTest.Resource;
import org.apache.camel.component.resteasy.test.beans.Customer;
import org.apache.camel.component.resteasy.test.beans.CustomerList;
import org.apache.camel.component.resteasy.test.beans.CustomerService;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebTest
public class ResteasyConsumerTest {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {

        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addClasses(Customer.class, CustomerService.class, CustomerList.class)
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml")
                        .importRuntimeAndTestDependencies().resolve().withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/contexts/basicConsumer.xml"), "applicationContext.xml")
                .addAsWebInfResource("web.xml");

    }

    private Response createCustomer(Customer customer) {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/createCustomer");
        Response response = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(customer, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(200, response.getStatus());
        return response;
    }

    private Response deleteCustomer(int id) {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/deleteCustomer?id=" + id);
        Response response = target.request().delete();

        assertEquals(200, response.getStatus());

        return response;
    }

    private Customer getCustomer(int id) {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/getCustomer?id=" + id);
        Response response = target.request().get();

        assertEquals(200, response.getStatus());

        return response.readEntity(Customer.class);
    }

    @Test
    @Order(1)
    public void testGetAll() throws Exception {
        String expectedUser1 = "{\"name\":\"Roman\",\"surname\":\"Jakubco\",\"id\":1}";
        String expectedUser2 = "{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2}";

        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/getAll");
        Response response = target.request().get();

        assertEquals(200, response.getStatus());

        String users = response.readEntity(String.class);
        assertTrue(users.contains(expectedUser1));
        assertTrue(users.contains(expectedUser2));

        File file = new File("target/test/consumerTest/all.txt");
        byte[] encoded = Files.readAllBytes(file.toPath());
        String responseBody = new String(encoded);

        assertTrue(responseBody.contains(expectedUser1));
        assertTrue(responseBody.contains(expectedUser2));
    }

    @Test
    public void testGet() throws Exception {
        Customer customer = getCustomer(2);

        assertEquals(new Customer("Camel", "Rider", 2), customer);

        File file = new File("target/test/consumerTest/get.txt");
        byte[] encoded = Files.readAllBytes(file.toPath());
        String responseBody = new String(encoded);

        assertEquals("{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2}", responseBody);
    }

    @Test
    public void testPost() throws Exception {

        String expectedResponse = "Customer added : Customer{name='TestCreate', surname='TestCreate', id=3}";
        int customerId = 3;

        Customer customer = new Customer("TestCreate", "TestCreate", customerId);
        Response response = createCustomer(customer);
        // assertEquals(expectedResponse,
        // response.readEntity(String.class));

        File file = new File("target/test/consumerTest/create.txt");
        byte[] encoded = Files.readAllBytes(file.toPath());
        String responseBody = new String(encoded);
        assertEquals(expectedResponse, responseBody);

        assertEquals(customer, getCustomer(customerId));

        deleteCustomer(customerId);
    }

    @Test
    public void testDelete() throws Exception {
        String expectedResponse = "Customer deleted : Customer{name='TestDelete', surname='TestDelete', id=4}";
        int customerId = 4;

        Customer customer = new Customer("TestDelete", "TestDelete", customerId);

        createCustomer(customer);
        Response responseDelete = deleteCustomer(customerId);

        assertEquals(200, responseDelete.getStatus());
        assertEquals(expectedResponse, responseDelete.readEntity(String.class));

        File file = new File("target/test/consumerTest/delete.txt");
        byte[] encoded = Files.readAllBytes(file.toPath());
        String responseBody = new String(encoded);
        assertEquals(expectedResponse, responseBody);

        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/getCustomer?id=" + customerId);
        Response response = target.request().get();

        assertEquals(404, response.getStatus());
        assertEquals("Customer with given id doesn't exist", response.readEntity(String.class));
    }

    @Test
    public void testPut() throws Exception {
        String expectedResponse = "Customer updated : Customer{name='TestPutUpdated', surname='TestPutUpdated', id=5}";
        int customerId = 5;

        Customer customer = new Customer("TestDelete", "TestDelete", customerId);

        createCustomer(customer);

        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/updateCustomer");

        customer.setName("TestPutUpdated");
        customer.setSurname("TestPutUpdated");
        Response response = target.request(MediaType.APPLICATION_JSON_TYPE)
                .put(Entity.entity(customer, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(200, response.getStatus());
        assertEquals(expectedResponse, response.readEntity(String.class));

        File file = new File("target/test/consumerTest/update.txt");
        byte[] encoded = Files.readAllBytes(file.toPath());
        String responseBody = new String(encoded);
        assertEquals(expectedResponse, responseBody);

        Customer updatedCustomer = getCustomer(customerId);
        assertEquals(customer, updatedCustomer);

        deleteCustomer(customerId);
    }

    @Test
    public void testWrongMethod() {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(baseUri.toString() + "customer/createCustomer");
        Response response = target.request().get();

        assertEquals(405, response.getStatus());
    }

}
