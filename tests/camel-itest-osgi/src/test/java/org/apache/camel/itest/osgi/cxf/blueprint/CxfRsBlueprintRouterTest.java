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
import org.apache.camel.itest.osgi.cxf.blueprint.jaxrs.testbean.CustomerService;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

@RunWith(JUnit4TestRunner.class)
@Ignore("This test will be failed with CXF 2.4.1, we need to use CXF 2.4.2")
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
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CxfRsBlueprintRouterTest)", 10000);
    }

    @Test
    public void testGetCustomer() throws Exception {
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers/123");
        get.addHeader("Accept" , "application/json");
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", 
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    

    @Test
    public void testGetCustomerWithQuery() throws Exception {      
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers?id=123");
        get.addHeader("Accept" , "application/json");
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", 
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
    @Test
    public void testGetCustomers() throws Exception {      
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers/");
        get.addHeader("Accept" , "application/xml");
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            // order returned can differ on OS so match for both orders
            String s = EntityUtils.toString(response.getEntity());
            boolean m1 = "<Customers><Customer><id>123</id><name>John</name></Customer><Customer><id>113</id><name>Dan</name></Customer></Customers>".equals(s);
            boolean m2 = "<Customers><Customer><id>113</id><name>Dan</name></Customer><Customer><id>123</id><name>John</name></Customer></Customers>".equals(s);

            if (!m1 && !m2) {
                fail("Not expected body returned: " + s);
            }
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
    @Test
    public void testGetSubResource() throws Exception {
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/orders/223/products/323");
        get.addHeader("Accept" , "application/json");
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}", 
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:9000/route/customerservice/customers");
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
        HttpPost post = new HttpPost("http://localhost:9000/route/customerservice/customers");
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

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
           
            // using the features to install the camel components
            scanFeatures(getCamelKarafFeatureUrl(),
                         "camel-blueprint","camel-http4", "camel-cxf"),
                                        
            bundle(newBundle()
                .add("OSGI-INF/blueprint/test.xml", CxfRsBlueprintRouterTest.class.getResource("CxfRsBlueprintRouter.xml"))
                .add(org.apache.camel.itest.osgi.cxf.blueprint.jaxrs.testbean.Customer.class)
                .add(org.apache.camel.itest.osgi.cxf.blueprint.jaxrs.testbean.CustomerService.class)
                .add(org.apache.camel.itest.osgi.cxf.blueprint.jaxrs.testbean.CustomerServiceResource.class)
                .add(org.apache.camel.itest.osgi.cxf.blueprint.jaxrs.testbean.Order.class)
                .add(org.apache.camel.itest.osgi.cxf.blueprint.jaxrs.testbean.Product.class)
                .set(Constants.BUNDLE_SYMBOLICNAME, "CxfRsBlueprintRouterTest")
                .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                .build()).noStart(),
            vmOption( "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5006" )

        );
          
        return options;
    }

}
