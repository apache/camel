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

import java.io.InputStream;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @version $Revision$
 */
@ContextConfiguration
public class CxfBeanTest extends AbstractJUnit4SpringContextTests {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>113</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";

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
    public void testGetConsumer() throws Exception {
        URL url = new URL("http://localhost:9000/customerservice/customers/123");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", CxfUtils.getStringFromInputStream(in));

        // START SNIPPET: clientInvocation
        url = new URL("http://localhost:9000/customerservice/orders/223/products/323");
        in = url.openStream();
        assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}", CxfUtils.getStringFromInputStream(in));
        // END SNIPPET: clientInvocation
    }
    
    @Test
    public void testGetConsumerWithQueryParam() throws Exception {
        URL url = new URL("http://localhost:9000/customerservice/customers?id=123");

        try {
            InputStream in = url.openStream();
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", CxfUtils.getStringFromInputStream(in));
        } catch (Exception ex) {
            Thread.sleep(3000000);
        }
    }

    @Test
    public void testGetConsumerAfterReStartCamelContext() throws Exception {
        URL url = new URL("http://localhost:9000/customerservice/customers/123");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", CxfUtils.getStringFromInputStream(in));
        in.close();

        camelContext.stop();
        camelContext.start();

        url = new URL("http://localhost:9000/customerservice/orders/223/products/323");
        in = url.openStream();
        
        assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}", 
                     CxfUtils.getStringFromInputStream(in));
        in.close();
    }
    
    @Test
    public void testGetConsumerAfterResumingCamelContext() throws Exception {
        URL url = new URL("http://localhost:9000/customerservice/customers/123");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", CxfUtils.getStringFromInputStream(in));
        in.close();

        camelContext.suspend();
        camelContext.resume();

        url = new URL("http://localhost:9000/customerservice/orders/223/products/323");
        in = url.openStream();

        assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}",
                     CxfUtils.getStringFromInputStream(in));
        in.close();
    }

    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:9000/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        put.setEntity(entity);
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(put);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
    @Test
    public void testPostConsumer() throws Exception {
        HttpPost post = new HttpPost("http://localhost:9000/customerservice/customers");
        post.addHeader("Accept" , "text/xml");
        StringEntity entity = new StringEntity(POST_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>124</id><name>Jack</name></Customer>",
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
    @Test
    public void testPostConsumerUniqueResponseCode() throws Exception {
        HttpPost post = new HttpPost("http://localhost:9000/customerservice/customersUniqueResponseCode");
        post.addHeader("Accept" , "text/xml");
        StringEntity entity = new StringEntity(POST_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(201, response.getStatusLine().getStatusCode());
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>125</id><name>Jack</name></Customer>",
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testJaxWsBean() throws Exception {        
        HttpPost post = new HttpPost("http://localhost:9090/customerservice/customers");
        post.addHeader("Accept" , "text/xml");
        String body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\">" 
            + "<personId>hello</personId></GetPerson></soap:Body></soap:Envelope>";
        
        StringEntity entity = new StringEntity(body, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getStatusLine().getStatusCode());
            String responseBody = EntityUtils.toString(response.getEntity());
            String correct = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
                + "<personId>hello</personId><ssn>000-000-0000</ssn><name>Bonjour</name></GetPersonResponse></soap:Body></soap:Envelope>";
            
            assertEquals("Get a wrong response", correct, responseBody);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
    @Test
    public void testJaxWsBeanFromCxfRoute() throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first", "PersonService"));
        Person client = ss.getSoap();
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
