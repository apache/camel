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
package org.apache.camel.component.restlet;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.InputRepresentation;
import org.restlet.representation.StringRepresentation;

/**
 * @version 
 */
public class RestletSetBodyTest extends RestletTestSupport {
    protected static int portNum2 =  AvailablePortFinder.getNextAvailable(4000);

    @Test
    public void testSetBody() throws Exception {
        String response = template.requestBody("restlet:http://localhost:" + portNum + "/stock/ORCL?restletMethod=get", null, String.class);
        assertEquals("110", response);
       
    }
    
    @Test
    public void testSetBodyRepresentation() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + portNum + "/images/123");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();
        InputStream is = null;
        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("image/png", response.getEntity().getContentType().getValue());
            is = response.getEntity().getContent();
            assertEquals("Get wrong available size", 10, response.getEntity().getContentLength());
            byte[] buffer = new byte[10];
            is.read(buffer);
            for (int i = 0; i < 10; i++) {
                assertEquals(i + 1, buffer[i]);
            }
        } finally {
            httpclient.close();
            if (is != null) {
                is.close();
            }
        }
    }
    
    @Test
    public void testGzipEntity() {
        String response = template.requestBody("restlet:http://localhost:" + portNum + "/gzip/data?restletMethod=get", null, String.class);
        assertEquals("Hello World!", response);
    }
    

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/stock/{symbol}?restletMethods=get")
                    .to("http://localhost:" + portNum2 + "/test?bridgeEndpoint=true")
                    //.removeHeader("Transfer-Encoding")
                    .setBody().constant("110");
                
                from("jetty:http://localhost:" + portNum2 + "/test").setBody().constant("response is back");

                // create ByteArrayRepresentation for response
                byte[] image = new byte[10];
                for (int i = 0; i < 10; i++) {
                    image[i] = (byte)(i + 1);
                }
                ByteArrayInputStream inputStream = new ByteArrayInputStream(image);

                from("restlet:http://localhost:" + portNum + "/images/{symbol}?restletMethods=get")
                    .setBody().constant(new InputRepresentation(inputStream, MediaType.IMAGE_PNG, 10));
                
                from("restlet:http://localhost:" + portNum + "/gzip/data?restletMethods=get")
                    .setBody().constant(new EncodeRepresentation(Encoding.GZIP, new StringRepresentation("Hello World!", MediaType.TEXT_XML)));
            }
        };
    }
}
