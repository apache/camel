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
package org.apache.camel.coap;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.junit.Test;

public class CoAPRestContextPathTest extends CoAPTestSupport {

    @Test
    public void testCoAPRestContextPath() throws Exception {
        CoapClient client = createClient("/rest/services/test/a");
        CoapResponse response = client.get();
        assertEquals(ResponseCode.CONTENT, response.getCode());
        assertEquals("GET: /test/a", response.getResponseText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration("coap")
                    .host("localhost")
                    .port(PORT)
                    .contextPath("/rest/services");

                rest("/test")
                    .get("/a")
                        .route()
                            .setBody(constant("GET: /test/a"));
            }
        };
    }
}
