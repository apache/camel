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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 *
 * @version $Revision$
 */
@ContextConfiguration
public class CxfBeanTest extends AbstractJUnit38SpringContextTests {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";
    
    @Autowired
    protected CamelContext context;
    
    @Override
    public void setUp() throws Exception {
        RouteBuilder builder = createRouteBuilder();
        context.addRoutes(builder);
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:9000?matchOnUriPrefix=true").to("cxfbean:customerServiceBean");   

            }
            
        };
    }   
    
    public void testGetConsumer() throws Exception {
        
        URL url = new URL("http://localhost:9000/customerservice/customers/123");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", getStringFromInputStream(in));

        url = new URL("http://localhost:9000/customerservice/orders/223/products/323");
        in = url.openStream();
        assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}", getStringFromInputStream(in));
        
    }

    
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
    
    private static String getStringFromInputStream(InputStream in) throws Exception {
        CachedOutputStream bos = new CachedOutputStream();
        IOUtils.copy(in, bos);
        in.close();
        bos.close();
        return bos.getOut().toString();
    }


}
