/*
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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.junit.Test;

/**
 * https://issues.apache.org/jira/browse/CAMEL-10985 CORS configuration is
 * ignored and REST endpoints function as per normal
 */
public class CoAPCORSTest extends CoAPTestSupport {
    private static final String COAP_RESPONSE = "{ \"foo\": \"bar\" }";

    @Test
    public void testEnableCors() throws Exception {
        CoapClient client = createClient("/rest");
        CoapResponse response = client.get();
        assertEquals(COAP_RESPONSE, response.getResponseText());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("coap").port(PORT).enableCORS(true);

                rest().get("/rest").route().setBody(constant(COAP_RESPONSE));
            }
        };
    }
}
