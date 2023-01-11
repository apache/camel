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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.MediaType;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.resteasy.ResteasyComponent;
import org.apache.camel.component.resteasy.ResteasyConstants;
import org.apache.camel.component.resteasy.test.WebTest.Deployment;
import org.apache.camel.component.resteasy.test.WebTest.Resource;
import org.apache.camel.component.resteasy.test.beans.Customer;
import org.apache.camel.component.resteasy.test.beans.CustomerList;
import org.apache.camel.component.resteasy.test.beans.CustomerService;
import org.apache.camel.component.resteasy.test.beans.TestBean;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebTest
public class ResteasyProducerTest extends CamelTestSupport {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {

        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addClasses(Customer.class, TestBean.class, CustomerService.class, CustomerList.class)
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeAndTestDependencies().resolve()
                        .withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/webWithoutAppContext.xml"), "web.xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                ResteasyComponent resteasy = new ResteasyComponent();
                CamelContext camelContext = getContext();
                camelContext.addComponent("resteasy", resteasy);

                DataFormat dataFormat = new JacksonDataFormat(Customer.class);

                from("direct:getAll").to("resteasy:" + baseUri.toString() + "customer/getAll?resteasyMethod=GET");

                from("direct:get").to("resteasy:" + baseUri.toString() + "customer/getCustomer?resteasyMethod=GET");

                from("direct:getUnmarshal").to("resteasy:" + baseUri.toString() + "customer/getCustomer?resteasyMethod=GET")
                        .unmarshal(dataFormat);

                from("direct:post").to("resteasy:" + baseUri.toString() + "customer/createCustomer?resteasyMethod=POST");

                from("direct:postInHeader").marshal(dataFormat)
                        .to("resteasy:" + baseUri.toString() + "customer/createCustomer");

                from("direct:postMarshal").marshal(dataFormat)
                        .to("resteasy:" + baseUri.toString() + "customer/createCustomer?resteasyMethod=POST");

                from("direct:put").marshal(dataFormat)
                        .to("resteasy:" + baseUri.toString() + "customer/updateCustomer?resteasyMethod=PUT");

                from("direct:delete").to("resteasy:" + baseUri.toString() + "customer/deleteCustomer?resteasyMethod=DELETE");

                from("direct:queryHeader").to("resteasy:" + baseUri.toString() + "customer/getAll?resteasyMethod=GET");

                from("direct:methodHeader").to("resteasy:" + baseUri.toString() + "customer/getAll?resteasyMethod=GET");

                from("direct:wrongMethod").to("resteasy:" + baseUri.toString() + "customer/getAll?resteasyMethod=GET");

                from("direct:notExisting").to("resteasy:" + baseUri.toString() + "customer/getAll?resteasyMethod=GET");
            }
        };
    }

    @Test
    public void testGet() {
        String expectedUser1 = "{\"name\":\"Roman\",\"surname\":\"Jakubco\",\"id\":1}";
        String expectedUser2 = "{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2}";
        String response = template.requestBody("direct:getAll", null, String.class);
        assertTrue(response.contains(expectedUser1));
        assertTrue(response.contains(expectedUser2));
    }

    @Test
    public void testGetWithQuery() {
        String expectedBody = "{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2}";

        String response = template.requestBodyAndHeader("direct:get", null, Exchange.HTTP_QUERY, "id=2", String.class);
        assertEquals(expectedBody, response);
    }

    @Test
    public void testGetWithQueryUnmarshal() {
        Integer customerId = 2;
        Customer expectedCustomer = new Customer("Camel", "Rider", customerId);
        Customer customer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY, "id=" + customerId,
                Customer.class);
        assertEquals(expectedCustomer, customer);
    }

    @Test
    public void testPost() {
        Integer customerId = 3;
        Customer expectedCustomer = new Customer("TestPost", "TestPost", customerId);
        String response
                = template.requestBodyAndHeader("direct:post", "{\"name\":\"TestPost\",\"surname\":\"TestPost\",\"id\":3}",
                        Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        Customer customer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY, "id=" + customerId,
                Customer.class);
        assertEquals(expectedCustomer, customer);

        template.sendBodyAndHeader("direct:delete", null, Exchange.HTTP_QUERY, "id=" + customerId);
    }

    @Test
    public void testPostMarshal() {
        Integer customerId = 4;
        Customer expectedCustomer = new Customer("TestPostMarshal", "TestPostMarshal", customerId);

        String response = template.requestBodyAndHeader("direct:postMarshal", expectedCustomer, Exchange.CONTENT_TYPE,
                MediaType.APPLICATION_JSON, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        Customer customer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY, "id=" + customerId,
                Customer.class);
        assertEquals(expectedCustomer, customer);

        template.sendBodyAndHeader("direct:delete", null, Exchange.HTTP_QUERY, "id=" + customerId);
    }

    @Test
    public void testPut() {
        Integer customerId = 5;
        Customer customer = new Customer("TestPut", "TestPut", customerId);

        String response = template.requestBodyAndHeader("direct:postMarshal", customer, Exchange.CONTENT_TYPE,
                MediaType.APPLICATION_JSON, String.class);
        assertEquals("Customer added : " + customer, response);

        Customer oldCustomer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY,
                "id=" + customerId, Customer.class);
        assertEquals(customer, oldCustomer);

        oldCustomer.setName("Updated");
        oldCustomer.setSurname("updated");

        response = template.requestBodyAndHeader("direct:put", oldCustomer, Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON,
                String.class);
        assertEquals("Customer updated : " + oldCustomer, response);

        Customer updatedCustomer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY,
                "id=" + customerId, Customer.class);
        assertEquals(oldCustomer, updatedCustomer);
        assertNotEquals(customer, updatedCustomer);

        template.sendBodyAndHeader("direct:delete", null, Exchange.HTTP_QUERY, "id=" + customerId);
    }

    @Test
    public void testDelete() {
        Integer customerId = 6;
        Customer expectedCustomer = new Customer("TestDelete", "TestDelete", customerId);

        String response = template.requestBodyAndHeader("direct:postMarshal", expectedCustomer, Exchange.CONTENT_TYPE,
                MediaType.APPLICATION_JSON, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        Customer customer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY, "id=" + customerId,
                Customer.class);
        assertEquals(expectedCustomer, customer);

        template.sendBodyAndHeader("direct:delete", null, Exchange.HTTP_QUERY, "id=" + customerId);

        response = template.requestBodyAndHeader("direct:get", null, Exchange.HTTP_QUERY, "id=" + customerId, String.class);
        assertEquals("Customer with given id doesn't exist", response);
    }

    @Test
    public void testMethodInHeader() {
        Integer customerId = 7;
        Customer expectedCustomer = new Customer("TestPostInHeader", "TestPostInHeader", customerId);

        //check default value for http method
        Exchange exchange = template.request("direct:postInHeader", new Processor() {
            @Override
            public void process(Exchange exchange) {

            }
        });

        assertEquals(405, exchange.getMessage().getHeaders().get("CamelHttpResponseCode"));

        Map<String, Object> headers = new HashMap<>();

        headers.put(ResteasyConstants.RESTEASY_HTTP_METHOD, "POST");
        headers.put(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        String response = template.requestBodyAndHeaders("direct:postInHeader", expectedCustomer, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        Customer customer = template.requestBodyAndHeader("direct:getUnmarshal", null, Exchange.HTTP_QUERY, "id=" + customerId,
                Customer.class);
        assertEquals(expectedCustomer, customer);

        template.sendBodyAndHeader("direct:delete", null, Exchange.HTTP_QUERY, "id=" + customerId);
    }

    @Test
    public void testSettingNotExistingHttpMethod() {
        assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:getAll", null, ResteasyConstants.RESTEASY_HTTP_METHOD, "GAT"));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testHead() {
        Exchange exchange = template.request("direct:getAll", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(ResteasyConstants.RESTEASY_HTTP_METHOD, "HEAD");
            }
        });

        Map<String, Object> headers = exchange.getMessage().getHeaders();
        ArrayList contentType = (ArrayList) headers.get("Content-Type");
        //ArrayList server = (ArrayList) headers.get("Server");
        ArrayList contentLength = (ArrayList) headers.get("Content-Length");
        Integer responseCode = (Integer) headers.get("CamelHttpResponseCode");

        assertEquals("application/json", contentType.get(0));
        //assertEquals("WildFly/8", server.get(0));
        assertEquals("87", contentLength.get(0));
        assertEquals(Integer.valueOf(200), responseCode);
    }

}
