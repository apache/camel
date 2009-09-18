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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;


/**
 *
 * @version $Revision$
 */
@ContextConfiguration
public class CxfBeanTest extends AbstractJUnit4SpringContextTests {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>113</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";
    
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
    public void testPutConsumer() throws Exception {
        PutMethod put = new PutMethod("http://localhost:9000/customerservice/customers");
        RequestEntity entity = new StringRequestEntity(PUT_REQUEST, "text/xml", "ISO-8859-1");
        put.setRequestEntity(entity);
        HttpClient httpclient = new HttpClient();

        try {
            assertEquals(200, httpclient.executeMethod(put));
            assertEquals("", put.getResponseBodyAsString());
        } finally {
            put.releaseConnection();
        }
    }
    
    @Test
    public void testPostConsumer() throws Exception {
        PostMethod post = new PostMethod("http://localhost:9000/customerservice/customers");
        post.addRequestHeader("Accept" , "text/xml");
        RequestEntity entity = new StringRequestEntity(POST_REQUEST, "text/xml", "ISO-8859-1");
        post.setRequestEntity(entity);
        HttpClient httpclient = new HttpClient();

        try {
            assertEquals(200, httpclient.executeMethod(post));
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>124</id><name>Jack</name></Customer>",
                    post.getResponseBodyAsString());
        } finally {
            post.releaseConnection();
        }

    }
    
    @Test
    public void testPostConsumerUniqueResponseCode() throws Exception {
        PostMethod post = new PostMethod("http://localhost:9000/customerservice/customersUniqueResponseCode");
        post.addRequestHeader("Accept" , "text/xml");
        RequestEntity entity = new StringRequestEntity(POST_REQUEST, "text/xml", "ISO-8859-1");
        post.setRequestEntity(entity);
        HttpClient httpclient = new HttpClient();

        try {
            assertEquals(201, httpclient.executeMethod(post));
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>125</id><name>Jack</name></Customer>",
                    post.getResponseBodyAsString());
        } finally {
            post.releaseConnection();
        }

    }

    @Test
    public void testJaxWsBean() throws Exception {        
        PostMethod post = new PostMethod("http://localhost:9090/customerservice/customers");
        post.addRequestHeader("Accept" , "text/xml");
        String body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\">" 
            + "<personId>hello</personId></GetPerson></soap:Body></soap:Envelope>";
        
        RequestEntity entity = new StringRequestEntity(body, "text/xml", "ISO-8859-1");
        post.setRequestEntity(entity);
        HttpClient httpclient = new HttpClient();

        try {
            assertEquals(200, httpclient.executeMethod(post));
            String response = post.getResponseBodyAsString();
            String correct = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
                + "<personId>hello</personId><ssn>000-000-0000</ssn><name>Bonjour</name></GetPersonResponse></soap:Body></soap:Envelope>";
            
            assertEquals("Get a wrong response", correct, response);
        } finally {
            post.releaseConnection();
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
