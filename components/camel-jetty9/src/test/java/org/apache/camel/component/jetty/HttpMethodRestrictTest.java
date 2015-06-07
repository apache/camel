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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.junit.Test;

public class HttpMethodRestrictTest extends BaseJettyTest {

    private String getUrl() {
        return "http://localhost:" + getPort() + "/methodRestrict";
    }
    
    @Test
    public void testProperHttpMethod() throws Exception {
        HttpClient httpClient = new HttpClient();
        PostMethod httpPost = new PostMethod(getUrl());

        StringRequestEntity reqEntity = new StringRequestEntity("This is a test", null, null);
        httpPost.setRequestEntity(reqEntity);

        int status = httpClient.executeMethod(httpPost);

        assertEquals("Get a wrong response status", 200, status);

        String result = httpPost.getResponseBodyAsString();
        assertEquals("Get a wrong result", "This is a test response", result);
    }

    @Test
    public void testImproperHttpMethod() throws Exception {
        HttpClient httpClient = new HttpClient();
        GetMethod httpGet = new GetMethod(getUrl());
        int status = httpClient.executeMethod(httpGet);

        assertEquals("Get a wrong response status", 405, status);
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                                
                from("jetty://http://localhost:{{port}}/methodRestrict?httpMethodRestrict=POST").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        String request = in.getBody(String.class);
                        exchange.getOut().setBody(request + " response");
                    }
                });
            }
        };
    }
}
