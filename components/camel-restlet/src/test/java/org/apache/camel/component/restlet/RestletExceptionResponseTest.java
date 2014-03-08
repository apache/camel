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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

/**
 *
 * @version
 */
public class RestletExceptionResponseTest extends RestletTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/users/{username}?restletMethod=POST").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.setException(new IllegalArgumentException("Damn something went wrong"));
                    }
                });
                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/tester?restletMethod=POST");
            }
        };
    }

    @Test
    public void testExceptionResponse() throws Exception {
        HttpResponse response = doExecute(new HttpPost("http://localhost:" + portNum + "/users/homer"));
        String body = EntityUtils.toString(response.getEntity());

        assertHttpResponse(response, 500, "text/plain");
        assertTrue(body.contains("IllegalArgumentException"));
        assertTrue(body.contains("Damn something went wrong"));
    }

    @Test
    public void testRestletProducerGetExceptionResponse() throws Exception {
        sendRequest("restlet:http://localhost:" + portNum + "/users/tester?restletMethod=POST");
    }

    @Test
    public void testSendRequestDirectEndpoint() throws Exception {
        sendRequest("direct:start");
    }

    protected void sendRequest(String endpointUri) throws Exception {
        Exchange exchange = template.request(endpointUri, new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setBody("<order foo='1'/>");
                }
            });
        RestletOperationException exception = (RestletOperationException) exchange.getException();
        String body = exception.getResponseBody();
        assertEquals("http://localhost:" + portNum + "/users/tester", exception.getUri());
        assertTrue(body.contains("IllegalArgumentException"));
        assertTrue(body.contains("Damn something went wrong"));
    }

}
