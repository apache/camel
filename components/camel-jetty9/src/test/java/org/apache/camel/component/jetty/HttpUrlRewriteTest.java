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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class HttpUrlRewriteTest extends BaseJettyTest {

    private int port1;
    private int port2;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRewrite", new MyUrlRewrite());
        return jndi;
    }

    @Test
    public void testUrlRewrite() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String response = template.requestBodyAndHeader("http://localhost:" + port1 + "/foo?phrase=Bye", "Camel", Exchange.HTTP_METHOD, "POST", String.class);
        assertEquals("Get a wrong response", "Bye Camel", response);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                port1 = getPort();
                port2 = getNextPort();

                from("jetty:http://localhost:" + port1 + "?matchOnUriPrefix=true")
                    .to("http://localhost:" + port2 + "?throwExceptionOnFailure=false&bridgeEndpoint=true&urlRewrite=#myRewrite");

                from("jetty://http://localhost:" + port2 + "/bar")
                        .transform().simple("${header.phrase} ${body}")
                        .to("mock:result");
            }
        };
    }    

}
