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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Test;

/**
 * This unit test verifies a single route can service multiple methods.
 * 
 * @version $Revision$
 */
public class RestletMultiMethodsEndpointTest extends CamelTestSupport {

    @Test
    public void testPostMethod() throws Exception {
        HttpMethod method = new PostMethod("http://localhost:9080/users/homer");
        try {
            HttpClient client = new HttpClient();
            assertEquals(200, client.executeMethod(method));
            assertTrue(method.getResponseHeader("Content-Type").getValue().startsWith("text/plain"));
            assertEquals("POST", method.getResponseBodyAsString());
        } finally {
            method.releaseConnection();
        }

    }

    @Test
    public void testGetMethod() throws Exception {
        HttpMethod method = new GetMethod("http://localhost:9080/users/homer");
        try {
            HttpClient client = new HttpClient();
            assertEquals(200, client.executeMethod(method));
            assertTrue(method.getResponseHeader("Content-Type").getValue().startsWith("text/plain"));
            assertEquals("GET", method.getResponseBodyAsString());
        } finally {
            method.releaseConnection();
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: routeDefinition
                from("restlet:http://localhost:9080/users/{username}?restletMethods=post,get")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // echo the method
                            exchange.getOut().setBody(exchange.getIn().getHeader(Exchange.HTTP_METHOD,
                                                                                 String.class));

                        }
                    });
                // END SNIPPET: routeDefinition
            }
        };
    }

}
