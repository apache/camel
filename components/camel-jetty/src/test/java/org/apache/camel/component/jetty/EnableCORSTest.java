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

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Test;

public class EnableCORSTest extends BaseJettyTest {

    @Test
    public void testCORSdisabled() throws Exception {
        HttpClient httpclient = new HttpClient();
        HttpMethod httpMethod = new GetMethod("http://localhost:" + getPort() + "/test1");
        httpMethod.addRequestHeader("Origin", "http://localhost:9000");
        httpMethod.addRequestHeader("Referer", "http://localhost:9000");

        int status = httpclient.executeMethod(httpMethod);

        assertEquals("Get a wrong response status", 200, status);

        Header responseHeader = httpMethod.getResponseHeader("Access-Control-Allow-Credentials");
        assertNull("Access-Control-Allow-Credentials HEADER should not be set", responseHeader);
    }


    @Test
    public void testCORSenabled() throws Exception {
        HttpClient httpclient = new HttpClient();
        HttpMethod httpMethod = new GetMethod("http://localhost:" + getPort2() + "/test2");
        httpMethod.addRequestHeader("Origin", "http://localhost:9000");
        httpMethod.addRequestHeader("Referer", "http://localhost:9000");


        int status = httpclient.executeMethod(httpMethod);

        assertEquals("Get a wrong response status", 200, status);

        Header responseHeader = httpMethod.getResponseHeader("Access-Control-Allow-Credentials");
        assertTrue("CORS not enabled", Boolean.valueOf(responseHeader.getValue()));


    }


    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty://http://localhost:{{port}}/test1?enableCORS=false").transform(simple("OK"));
                from("jetty://http://localhost:{{port2}}/test2?enableCORS=true").transform(simple("OK"));
            }
        };
    }
}
