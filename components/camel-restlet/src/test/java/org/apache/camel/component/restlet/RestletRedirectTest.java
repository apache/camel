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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

/**
 *
 * @version 
 */
public class RestletRedirectTest extends RestletTestSupport {

    @Test
    public void testRedirect() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + portNum + "/users/homer");

        // do not follow redirects
        RequestConfig requestconfig = RequestConfig.custom().setRedirectsEnabled(false).build();
        
        CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(requestconfig).build();
        
        try {
            
            HttpResponse response = client.execute(get);
    
            for (Header header : response.getAllHeaders()) {
                log.info("Header {}", header);
            }
    
            assertEquals(302, response.getStatusLine().getStatusCode());
            assertTrue("Should have location header", response.containsHeader("Location"));
            assertEquals("http://somewhere.com", response.getFirstHeader("Location").getValue());
            assertEquals("bar", response.getFirstHeader("Foo").getValue());
        } finally {
            client.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/users/{username}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(302))
                    .setHeader("Location", constant("http://somewhere.com"))
                    .setHeader("Foo", constant("bar"));
            }
        };
    }

}