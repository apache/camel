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

package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsRouterTest extends CamelSpringTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String POST_REQUEST = "<Customer><name>Jack</name></Customer>";

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {        
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringRouter.xml");
    }
    
    @Test
    public void testGetCustomer() throws Exception {      
        GetMethod get = new GetMethod("http://localhost:9000/customerservice/customers/123");
        get.addRequestHeader("Accept" , "application/json");
        
        HttpClient httpclient = new HttpClient();

        try {
            assertEquals(200, httpclient.executeMethod(get));
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}", 
                         get.getResponseBodyAsString());
        } finally {
            get.releaseConnection();
        }
    }
    
    @Test
    public void testGetSubResource() throws Exception {
        GetMethod get = new GetMethod("http://localhost:9000/customerservice/orders/223/products/323");
        get.addRequestHeader("Accept" , "application/json");

        HttpClient httpclient = new HttpClient();

        try {
            assertEquals(200, httpclient.executeMethod(get));
            assertEquals("{\"Product\":{\"description\":\"product 323\",\"id\":323}}", 
                         get.getResponseBodyAsString());
        } finally {
            get.releaseConnection();
        }
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
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Customer><id>124</id><name>Jack</name></Customer>",
                    post.getResponseBodyAsString());
        } finally {
            post.releaseConnection();
        }

    }

}
