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
package org.apache.camel.component.cxf.cxfbean;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@ContextConfiguration
public class CxfBeanTest extends AbstractJUnit4SpringContextTests {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>113</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";
    private static final String POST2_REQUEST = "<Customer><name>James</name></Customer>";
    private static final int PORT1 = CXFTestSupport.getPort("CxfBeanTest.1");
    private static final int PORT2 = CXFTestSupport.getPort("CxfBeanTest.2");
    
    @Autowired
    @Qualifier("camel")
    protected CamelContext camelContext;
    
    /**
     * Test that we have an endpoint with 2 providers.
     */
    @Test
    public void testConsumerWithProviders() throws Exception {
        boolean testedEndpointWithProviders = false;
        for (Endpoint endpoint : camelContext.getEndpoints()) {
            if (endpoint instanceof CxfBeanEndpoint) {
                CxfBeanEndpoint beanEndpoint = (CxfBeanEndpoint)endpoint;
                if (beanEndpoint.getEndpointUri().equals("customerServiceBean")) {
                    assertNotNull("The bean endpoint should have provider", beanEndpoint.getProviders());
                    if (beanEndpoint.getProviders().size() == 2) {
                        testedEndpointWithProviders = true;
                        break;
                    } else if (beanEndpoint.getProviders().size() != 0) {
                        fail("Unexpected number of providers present");
                    }
                }
            }
        }
        assertTrue(testedEndpointWithProviders);
    }
    
    @Test
    public void testMessageHeadersAfterCxfBeanEndpoint() throws Exception {
        MockEndpoint endpoint = (MockEndpoint)camelContext.getEndpoint("mock:endpointA");
        endpoint.reset();
        endpoint.expectedMessageCount(1);
        endpoint.expectedHeaderReceived("key", "customer");

        invokeRsService("http://localhost:" + PORT1 + "/customerservice/customers/123",
            "{\"Customer\":{\"id\":123,\"name\":\"John\"}}");

        endpoint.assertIsSatisfied();
    }
    
    private void invokeRsService(String getUrl, String expected) throws Exception {
        HttpGet get = new HttpGet(getUrl);
        get.addHeader("Accept", "application/json");
        get.addHeader("key", "customer");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals(expected,
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testGetConsumer() throws Exception {
        invokeRsService("http://localhost:" + PORT1 + "/customerservice/customers/123",
                        "{\"Customer\":{\"id\":123,\"name\":\"John\"}}");
        
        invokeRsService("http://localhost:" + PORT1 + "/customerservice/orders/223/products/323",
                         "{\"Product\":{\"description\":\"product 323\",\"id\":323}}");
    }
    
    @Test
    public void testGetConsumerWithQueryParam() throws Exception {
        invokeRsService("http://localhost:" + PORT1 + "/customerservice/customers?id=123",
                        "{\"Customer\":{\"id\":123,\"name\":\"John\"}}");        
    }

    @Test
    public void testGetConsumerAfterReStartCamelContext() throws Exception {
        invokeRsService("http://localhost:" + PORT1 + "/customerservice/customers/123",
                        "{\"Customer\":{\"id\":123,\"name\":\"John\"}}");

        camelContext.stop();
        camelContext.start();

        invokeRsService("http://localhost:" + PORT1 + "/customerservice/orders/223/products/323",
                        "{\"Product\":{\"description\":\"product 323\",\"id\":323}}"); 
    }
    
    @Test
    public void testGetConsumerAfterResumingCamelContext() throws Exception {
        invokeRsService("http://localhost:" + PORT1 + "/customerservice/customers/123",
                        "{\"Customer\":{\"id\":123,\"name\":\"John\"}}");
        
        camelContext.suspend();
        camelContext.resume();

        invokeRsService("http://localhost:" + PORT1 + "/customerservice/orders/223/products/323",
                        "{\"Product\":{\"description\":\"product 323\",\"id\":323}}"); 
    }

    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + PORT1 + "/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        put.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(put);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testPostConsumer() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + PORT1 + "/customerservice/customers");
        post.addHeader("Accept", "text/xml");
        StringEntity entity = new StringEntity(POST_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getStatusLine().getStatusCode());
            String id = getCustomerId("Jack");
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>" + id + "</id><name>Jack</name></Customer>",
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testPostConsumerUniqueResponseCode() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + PORT1 + "/customerservice/customersUniqueResponseCode");
        post.addHeader("Accept", "text/xml");
        StringEntity entity = new StringEntity(POST2_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(201, response.getStatusLine().getStatusCode());
            String id = getCustomerId("James");
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>" + id + "</id><name>James</name></Customer>",
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }

    private String getCustomerId(String name) throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + PORT1 + "/customerservice/customers/");
        get.addHeader("Accept", "application/xml");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            String customers = EntityUtils.toString(response.getEntity());
            String before = ObjectHelper.before(customers, "</id><name>" + name + "</name></Customer>");
            String answer = before.substring(before.lastIndexOf(">") + 1, before.length());
            return answer;
        } finally {
            httpclient.close();
        }
    }

    @Test
    public void testJaxWsBean() throws Exception {        
        HttpPost post = new HttpPost("http://localhost:" + PORT2 + "/customerservice/customers");
        post.addHeader("Accept", "text/xml");
        String body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\">" 
            + "<personId>hello</personId></GetPerson></soap:Body></soap:Envelope>";
        
        StringEntity entity = new StringEntity(body, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getStatusLine().getStatusCode());
            String responseBody = EntityUtils.toString(response.getEntity());
            String correct = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
                + "<personId>hello</personId><ssn>000-000-0000</ssn><name>Bonjour</name></GetPersonResponse></soap:Body></soap:Envelope>";
            
            assertEquals("Get a wrong response", correct, responseBody);
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testJaxWsBeanFromCxfRoute() throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first", "PersonService"));
        Person client = ss.getSoap();
        ((BindingProvider)client).getRequestContext()
            .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                 "http://localhost:" + CXFTestSupport.getPort1() + "/CxfBeanTest/PersonService/");
        
        Holder<String> personId = new Holder<String>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();
        client.getPerson(personId, ssn, name);
        assertEquals("Get a wrong personId", "hello", personId.value);
        assertEquals("Get a wrong SSN", "000-000-0000", ssn.value);
        assertEquals("Get a wrong name", "Bonjour", name.value);
    }

}
