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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Status;

/**
 * Testing that end users can use the {@link Response} API from Restlet directly to have fine grained
 * control of the response they want to use.
 *
 * @version 
 */
public class RestletRequestAndResponseAPITest extends RestletTestSupport {

    @Test
    public void testRestletProducer() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("id", 123);
        headers.put("beverage.beer", "Carlsberg");

        String out = template.requestBodyAndHeaders("direct:start", null, headers, String.class);
        assertEquals("<response>Beer is Good</response>", out);
    }

    @Test
    public void testRestletProducer2() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("id", 123);
        headers.put("beverage.beer", "Carlsberg");

        Exchange out = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeaders(headers);
            }
        });
        assertNotNull(out);
        assertEquals("text/xml", out.getOut().getHeader(Exchange.CONTENT_TYPE));
        assertEquals(200, out.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("<response>Beer is Good</response>", out.getOut().getBody(String.class));

        // the restlet response should be accessible if needed
        Response response = out.getOut().getHeader(RestletConstants.RESTLET_RESPONSE, Response.class);
        assertNotNull(response);
        assertEquals(200, response.getStatus().getCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/{id}/like/{beverage.beer}");

                // START SNIPPET: e1
                from("restlet:http://localhost:" + portNum + "/users/{id}/like/{beer}")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // the Restlet request should be available if needed
                            Request request = exchange.getIn().getHeader(RestletConstants.RESTLET_REQUEST, Request.class);
                            assertNotNull("Restlet Request", request);

                            // use Restlet API to create the response
                            Response response = exchange.getIn().getHeader(RestletConstants.RESTLET_RESPONSE, Response.class);
                            assertNotNull("Restlet Response", response);
                            response.setStatus(Status.SUCCESS_OK);
                            response.setEntity("<response>Beer is Good</response>", MediaType.TEXT_XML);
                            exchange.getOut().setBody(response);
                        }
                    });
                // END SNIPPET: e1
            }
        };
    }
}
