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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class CxfRsRouterTest extends CamelSpringTestSupport {
    private static final int PORT = CXFTestSupport.getPort1();

    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";

    protected int getPort() {
        return PORT;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringRouter.xml");
    }

    @Test
    public void testEndpointUris() throws Exception {
        CxfRsEndpoint cxfRsEndpoint = context.getEndpoint("cxfrs://bean://rsServer", CxfRsEndpoint.class);
        assertEquals("cxfrs://bean://rsServer", cxfRsEndpoint.getEndpointUri(), "Get a wrong endpoint uri");

        cxfRsEndpoint = context.getEndpoint("cxfrs://bean://rsClient", CxfRsEndpoint.class);
        assertEquals("cxfrs://bean://rsClient", cxfRsEndpoint.getEndpointUri(), "Get a wrong endpoint uri");

    }

    @Test
    public void testGetCustomer() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers/123");
        get.addHeader("Accept", "application/json");

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(get)) {
            assertEquals(200, response.getCode());
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}",
                    EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testGetCustomerWithQuery() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers?id=123");
        get.addHeader("Accept", "application/json");

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(get)) {
            assertEquals(200, response.getCode());
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}",
                    EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testGetCustomers() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers/");
        get.addHeader("Accept", "application/xml");

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(get)) {
            assertEquals(200, response.getCode());
            // order returned can differ on OS so match for both orders
            String s = EntityUtils.toString(response.getEntity());
            assertNotNull(s);
            boolean m1 = s.endsWith(
                    "<Customers><Customer><id>123</id><name>John</name></Customer><Customer><id>113</id><name>Dan</name></Customer></Customers>");
            boolean m2 = s.endsWith(
                    "<Customers><Customer><id>113</id><name>Dan</name></Customer><Customer><id>123</id><name>John</name></Customer></Customers>");

            if (!m1 && !m2) {
                fail("Not expected body returned: " + s);
            }
        }
    }

    @Test
    public void testGetSubResource() throws Exception {
        HttpGet get = new HttpGet(
                "http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/orders/223/products/323");
        get.addHeader("Accept", "application/json");

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(get)) {
            assertEquals(200, response.getCode());
            assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}",
                    EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(put)) {
            assertEquals(200, response.getCode());
            assertEquals("", EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testPostConsumer() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers");
        post.addHeader("Accept", "text/xml");
        StringEntity entity = new StringEntity(POST_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        post.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            CloseableHttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getCode());
            assertEquals(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>124</id><name>Jack</name></Customer>",
                    EntityUtils.toString(response.getEntity()));

            HttpDelete del
                    = new HttpDelete("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers/124/");
            response = httpclient.execute(del);
            // need to check the response of delete method
            assertEquals(200, response.getCode());
        }

    }

    @Test
    public void testPostConsumerUniqueResponseCode() throws Exception {
        HttpPost post = new HttpPost(
                "http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customersUniqueResponseCode");
        post.addHeader("Accept", "text/xml");
        StringEntity entity = new StringEntity(POST_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        post.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            CloseableHttpResponse response = httpclient.execute(post);
            assertEquals(201, response.getCode());
            assertEquals(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>124</id><name>Jack</name></Customer>",
                    EntityUtils.toString(response.getEntity()));

            HttpDelete del
                    = new HttpDelete("http://localhost:" + getPort() + "/CxfRsRouterTest/route/customerservice/customers/124/");
            response = httpclient.execute(del);
            // need to check the response of delete method
            assertEquals(200, response.getCode());
        }
    }
}
