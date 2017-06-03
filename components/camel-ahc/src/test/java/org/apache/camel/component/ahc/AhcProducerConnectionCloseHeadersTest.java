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
package org.apache.camel.component.ahc;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AhcProducerConnectionCloseHeadersTest extends BaseAhcTest {

    @Test
    public void testAhcProduce() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 123);
        getMockEndpoint("mock:result").expectedHeaderReceived("bar", "cool");
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.HTTP_RESPONSE_TEXT, "OK");
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.CONTENT_LENGTH, 9);
        getMockEndpoint("mock:result").expectedHeaderReceived("Connection", "close");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", 123);
        headers.put("bar", "cool");

        template.sendBodyAndHeaders("direct:start", null, headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to(getAhcEndpointUri() + "?connectionClose=true")
                    .to("mock:result");

                from(getTestServerEndpointUri())
                        // Remove the message header here
                        .removeHeaders("*")
                        .transform(constant("Bye World"));
            }
        };
    }
}
