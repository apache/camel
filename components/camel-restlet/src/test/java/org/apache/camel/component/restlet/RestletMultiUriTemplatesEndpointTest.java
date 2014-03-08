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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.junit.Test;

/**
 * This unit test verifies a single route can service multiple templates.
 * 
 * @version 
 */
public class RestletMultiUriTemplatesEndpointTest extends RestletTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        List<String> list = new ArrayList<String>();
        list.add("/users/{username}");
        list.add("/atom/collection/{id}/component/{cid}");

        JndiRegistry jndi = super.createRegistry();
        jndi.bind("uriTemplates", list);
        return jndi;
    }

    @Test
    public void testPostUserUriPattern() throws Exception {
        HttpResponse response = doExecute(new HttpPost("http://localhost:" + portNum + "/users/homer"));

        assertHttpResponse(response, 200, "text/plain", "POST homer");
    }

    @Test
    public void testGetAtomUriPattern() throws Exception {
        HttpResponse response = doExecute(new HttpGet("http://localhost:" + portNum + "/atom/collection/foo/component/bar"));

        assertHttpResponse(response, 200, "text/plain", "GET foo bar");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: routeDefinition
                from("restlet:http://localhost:" + portNum + "?restletMethods=post,get&restletUriPatterns=#uriTemplates")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            // echo the method
                            String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                            String out = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
                            if (("http://localhost:" + portNum + "/users/homer").equals(uri)) {
                                exchange.getOut().setBody(out + " " + exchange.getIn().getHeader("username", String.class));
                            } else if (("http://localhost:" + portNum + "/atom/collection/foo/component/bar").equals(uri)) {
                                exchange.getOut().setBody(out + " " + exchange.getIn().getHeader("id", String.class)
                                                          + " " + exchange.getIn().getHeader("cid", String.class));
                            }
                        }
                    });
                // END SNIPPET: routeDefinition
            }
        };
    }
}