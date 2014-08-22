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
package org.apache.camel.itest.osgi.cxf.blueprint;


import org.apache.camel.CamelContext;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;
import org.apache.camel.itest.osgi.cxf.jaxrs.testbean.CustomerService;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class CxfRsBlueprintRouterTest extends OSGiBlueprintTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";
    private static Server server;

    @BeforeClass
    public static void startServer() {
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setAddress("http://localhost:9002/rest");
        sf.setServiceBean(new CustomerService());
        sf.setStaticSubresourceResolution(true);
        server = sf.create();
    }
    
    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    protected void doPostSetup() throws Exception {
        getInstalledBundle("CxfRsBlueprintRouterTest").start();
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CxfRsBlueprintRouterTest)", 30000);
    }

    @Test
    public void testGetCustomer() throws Exception {
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers/123");
        get.addHeader("Accept" , "application/json");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());

            // should either by John or Mary depending on PUT test executed first
            String s = EntityUtils.toString(response.getEntity());
            boolean isJohn = "{\"Customer\":{\"id\":123,\"name\":\"John\"}}".equals(s);
            boolean isMary = "{\"Customer\":{\"id\":123,\"name\":\"Mary\"}}".equals(s);
            assertTrue("Should be John or Mary", isJohn || isMary);
        } finally {
            httpclient.close();
        }
    }
    

    @Test
    public void testGetCustomerWithQuery() throws Exception {      
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers?id=123");
        get.addHeader("Accept" , "application/json");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", 
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testGetCustomers() throws Exception {      
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers/");
        get.addHeader("Accept" , "application/xml");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            // order returned can differ on OS so match for both orders
            String s = EntityUtils.toString(response.getEntity());
            assertNotNull(s);
            boolean m1 = s.endsWith("<Customers><Customer><id>123</id><name>John</name></Customer><Customer><id>113</id><name>Dan</name></Customer></Customers>");
            boolean m2 = s.endsWith("<Customers><Customer><id>113</id><name>Dan</name></Customer><Customer><id>123</id><name>John</name></Customer></Customers>");

            if (!m1 && !m2) {
                fail("Not expected body returned: " + s);
            }
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testGetSubResource() throws Exception {
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/orders/223/products/323");
        get.addHeader("Accept" , "application/json");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}", 
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:9000/route/customerservice/customers");
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
        HttpPost post = new HttpPost("http://localhost:9000/route/customerservice/customers");
        post.addHeader("Accept" , "text/xml");
        StringEntity entity = new StringEntity(POST_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        post.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(post);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>124</id><name>Jack</name></Customer>",
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }

    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
           
            // using the features to install the camel components
            loadCamelFeatures(
                         "camel-blueprint", "camel-http4", "camel-cxf"),
                                        
            bundle(TinyBundles.bundle()
                .add("OSGI-INF/blueprint/test.xml", CxfRsBlueprintRouterTest.class.getResource("CxfRsBlueprintRouter.xml"))
                .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.Customer.class)
                .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.CustomerService.class)
                .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.CustomerServiceResource.class)
                .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.Order.class)
                .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.Product.class)
                .set(Constants.BUNDLE_SYMBOLICNAME, "CxfRsBlueprintRouterTest")
                .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                .build()).noStart()

        );
          
        return options;
    }

}
