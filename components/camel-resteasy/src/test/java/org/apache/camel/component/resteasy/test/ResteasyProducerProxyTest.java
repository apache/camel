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

import org.apache.camel.CamelContext;
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
import org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface;
import org.apache.camel.component.resteasy.test.beans.ResteasyProducerProxyTestApp;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebTest
public class ResteasyProducerProxyTest extends CamelTestSupport {

    @Resource
    URI baseUri;

    @Deployment
    public static Archive<?> createTestArchive() {

        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addClasses(Customer.class, CustomerService.class, CustomerList.class, ProxyProducerInterface.class,
                        ResteasyProducerProxyTestApp.class)
                .addPackage("org.apache.camel.component.resteasy")
                .addPackage("org.apache.camel.component.resteasy.servlet")
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeAndTestDependencies().resolve()
                        .withTransitivity().asFile())
                .addAsWebInfResource(new File("src/test/resources/webWithoutAppContext2.xml"), "web.xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                ResteasyComponent resteasy = new ResteasyComponent();
                CamelContext camelContext = getContext();
                camelContext.addComponent("resteasy", resteasy);

                DataFormat dataFormat = new JacksonDataFormat(Customer.class);

                from("direct:getAll")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=getAllCustomers");

                from("direct:get")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=getCustomer");

                from("direct:getUnmarshal")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=getCustomer")
                        .unmarshal(dataFormat);

                from("direct:post")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=createCustomer");

                from("direct:put").marshal(dataFormat)
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=updateCustomer");

                from("direct:delete")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=deleteCustomer");

                from("direct:moreAttributes")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=getSpecificThreeCustomers");

                from("direct:differentType")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=checkIfCustomerExists");

                from("direct:notResponseType")
                        .to("resteasy:" + baseUri.toString()
                            + "?proxyClientClass=org.apache.camel.component.resteasy.test.beans.ProxyProducerInterface"
                            + "&proxyMethod=getCustomerWithoutResponse");
            }
        };
    }

    private void deleteCustomer(Integer id) {
        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();

        params.clear();
        headers.clear();
        params.add(id);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:delete", null, headers, String.class);
        assertTrue(response.contains(String.valueOf(id)));
        assertTrue(response.contains("Customer deleted :"));
    }

    @Test
    @Order(1)
    public void testProxyGetAll() {
        String expectedUser1 = "{\"name\":\"Roman\",\"surname\":\"Jakubco\",\"id\":1}";
        String expectedUser2 = "{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2}";

        String response = template.requestBody("direct:getAll", null, String.class);
        assertTrue(response.contains(expectedUser1));
        assertTrue(response.contains(expectedUser2));
    }

    @Test
    public void testProxyGet() {
        String expectedBody = "{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2}";

        Exchange response = template.request("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) {
                ArrayList<Object> params = new ArrayList<Object>();
                params.add(2);
                exchange.getIn().getHeaders().put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
            }
        });
        assertEquals(expectedBody, response.getMessage().getBody(String.class));
    }

    @Test
    public void testProxyGetUnmarshal() {
        Customer expectedCustomer = new Customer("Camel", "Rider", 2);

        Exchange response = template.request("direct:getUnmarshal", new Processor() {
            @Override
            public void process(Exchange exchange) {
                ArrayList<Object> params = new ArrayList<Object>();
                params.add(2);

                exchange.getIn().getHeaders().put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
            }
        });
        assertEquals(expectedCustomer, response.getMessage().getBody(Customer.class));

    }

    @Test
    public void testProxyPost() {
        Integer customerId = 3;
        Customer expectedCustomer = new Customer("TestPost", "TestPost", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        deleteCustomer(customerId);
    }

    @Test
    public void testProxyPut() {
        Integer customerId = 4;
        Customer expectedCustomer = new Customer("TestPut", "TestPut", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        params.clear();
        headers.clear();
        expectedCustomer.setName("TestPutUpdated");
        expectedCustomer.setSurname("TestPutUpdated");
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        response = template.requestBodyAndHeaders("direct:put", null, headers, String.class);
        assertEquals("Customer updated : " + expectedCustomer, response);

        deleteCustomer(customerId);
    }

    @Test
    public void testProxyDelete() {
        Integer customerId = 5;
        Customer expectedCustomer = new Customer("TestDelete", "TestDelete", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        params.clear();
        headers.clear();
        params.add(customerId);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        response = template.requestBodyAndHeaders("direct:delete", null, headers, String.class);
        assertEquals("Customer deleted : " + expectedCustomer, response);

    }

    @Test
    public void testProxyCallWithMoreAttributes() {
        Integer customerId = 6;
        Customer expectedCustomer = new Customer("Test", "Test", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        String expectedCustomers
                = "[{\"name\":\"Test\",\"surname\":\"Test\",\"id\":6},{\"name\":\"Camel\",\"surname\":\"Rider\",\"id\":2},{\"name\":\"Roman\",\"surname\":\"Jakubco\",\"id\":1}]";
        headers.clear();
        params.clear();
        params.add(6);
        params.add(2);
        params.add(1);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
        response = template.requestBodyAndHeaders("direct:moreAttributes", null, headers, String.class);
        assertEquals(expectedCustomers, response);

        deleteCustomer(customerId);
    }

    @Test
    public void testProxyCallWithDifferentTypeAttributes() {
        Integer customerId = 7;
        Customer expectedCustomer = new Customer("TestAttr", "TestAttr", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);

        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        headers.clear();
        params.clear();
        params.add(7);
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
        response = template.requestBodyAndHeaders("direct:differentType", null, headers, String.class);
        assertEquals("Customers are equal", response);

        headers.clear();
        params.clear();
        params.add(1);
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
        response = template.requestBodyAndHeaders("direct:differentType", null, headers, String.class);
        assertEquals("Customers are not equal", response);

        Customer testCustomer = new Customer("Camel", "Rider", 2);
        headers.clear();
        params.clear();
        params.add(2);
        params.add(testCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
        response = template.requestBodyAndHeaders("direct:differentType", null, headers, String.class);
        assertEquals("Customers are equal", response);

        deleteCustomer(customerId);

    }

    @Test
    public void testProxyCallWithAttributesInWrongOrder() {
        Integer customerId = 8;
        final Customer expectedCustomer = new Customer("TestWrong", "TestWrong", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);

        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
        Exchange exchange = template.request("direct:differentType", new Processor() {
            @Override
            public void process(Exchange exchange) {
                ArrayList<Object> exchangeParams = new ArrayList<Object>();
                exchangeParams.add(expectedCustomer);
                exchangeParams.add(8);
                exchange.getIn().getHeaders().put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, exchangeParams);
            }
        });

        assertTrue(exchange.getMessage().getBody() instanceof NoSuchMethodException);
        assertTrue(exchange.getMessage().getHeaders().containsKey(ResteasyConstants.RESTEASY_PROXY_PRODUCER_EXCEPTION));

        deleteCustomer(customerId);
    }

    @Test
    public void testProxyCallOnMethodWithoutReturnTypeResponse() {
        Integer customerId = 9;
        Customer expectedCustomer = new Customer("Test", "Test", customerId);

        Map<String, Object> headers = new HashMap<>();
        ArrayList<Object> params = new ArrayList<Object>();
        params.add(expectedCustomer);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);

        String response = template.requestBodyAndHeaders("direct:post", null, headers, String.class);
        assertEquals("Customer added : " + expectedCustomer, response);

        headers.clear();
        params.clear();
        params.add(9);
        headers.put(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, params);
        response = template.requestBodyAndHeaders("direct:notResponseType", null, headers, String.class);

        assertEquals(expectedCustomer.toString(), response);

        deleteCustomer(customerId);

    }

}
