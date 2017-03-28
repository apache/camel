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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore("Jetty 9.3 treats an empty header as an empty string, like Jetty 8")
public class HttpProducerSendEmptyHeaderTest extends BaseJettyTest {

    @Test
    public void testHttpProducerSendEmptyHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        
        // Jetty 8 treats an empty header as "" while Jetty 9 treats it as null
        String expectedValue = isJetty8() ? "" : null; 
        mock.expectedHeaderReceived("foo", expectedValue);

        template.sendBodyAndHeader("http://localhost:{{port}}/myapp/mytest", "Hello World", "foo", "");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        allowNullHeaders();
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/mytest")
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }
}
