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
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;

/**
 *
 * @version 
 */
public class RestletPostFormTest extends RestletTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/users?restletMethod=POST")
                    .process(new PostProcessor());
            }
        };
    }
    
    class PostProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {   
            assertEquals("bar", exchange.getIn().getHeader("foo", String.class));
            exchange.getIn().setBody("OK");
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
        }
    }
    
    @Test
    public void testPostBody() throws Exception {
        HttpUriRequest method = new HttpPost("http://localhost:" + portNum + "/users");

        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("foo", "bar"));

        ((HttpEntityEnclosingRequestBase)method).setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = doExecute(method);
        
        assertHttpResponse(response, 200, "text/plain");
    }
}