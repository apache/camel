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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The CoAP consumer maps URI query parameters of an incoming request into Exchange headers. A remote peer must not be
 * able to use that to set Camel internal headers, whatever casing it uses, as those steer downstream processing (bean
 * method dispatch, file names, and so on).
 */
public class CoAPHeaderInjectionTest extends CoAPTestSupport {

    private static final String[] CAMEL_HEADER_VARIANTS = {
            "CamelBeanMethodName", "camelBeanMethodName", "caMELBeanMethodName", "CAMELBEANMETHODNAME" };

    @Test
    void camelHeadersInUriQueryAreFilteredRegardlessOfCase() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(CAMEL_HEADER_VARIANTS.length);

        CoapClient client = createClient("/TestResource");
        for (String variant : CAMEL_HEADER_VARIANTS) {
            Request request = Request.newPost();
            request.setURI(client.getURI() + "?" + variant + "=malicious&normalParam=value");
            request.setPayload("test");
            request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
            assertNotNull(client.advanced(request), "no CoAP response received for variant " + variant);
        }

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> received = mock.getReceivedExchanges();
        for (int i = 0; i < CAMEL_HEADER_VARIANTS.length; i++) {
            String variant = CAMEL_HEADER_VARIANTS[i];
            Message in = received.get(i).getIn();
            // the Camel header map is case-insensitive, so this lookup also catches the other spellings
            assertNull(in.getHeader(variant),
                    "a Camel internal header must not be injectable through a CoAP URI query parameter: " + variant);
            assertEquals("value", in.getHeader("normalParam", String.class),
                    "a non-Camel query parameter must still be mapped to a header");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/TestResource", PORT)
                        .to("mock:result")
                        .setBody(constant("ok"));
            }
        };
    }
}
