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
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;

/**
 *
 * @version 
 */
public class RestletQueryTest extends RestletTestSupport {
    private static final String QUERY_STRING = "foo=bar&test=123";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/users/{username}").process(new SetUserProcessor());
                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/{username}");
            }
        };
    }
    
    class SetUserProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {   
            assertEquals(QUERY_STRING, exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class));
        }
        
    }
    
    @Test
    public void testPostBody() throws Exception {
        HttpResponse response = doExecute(new HttpGet("http://localhost:" + portNum + "/users/homer?" + QUERY_STRING));

        assertHttpResponse(response, 204, "text/plain");
    }
    
    
    @Test
    public void testGetBodyByRestletProducer() throws Exception {        
        Exchange ex = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_QUERY, QUERY_STRING);
                exchange.getIn().setHeader("username", "homer");
                
            }
        });
        assertEquals(204, ex.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        
    }
}