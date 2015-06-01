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
package org.apache.camel.component.jetty.jettyproducer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 *
 */
public class JettyHttpProducerBridgePathWithSpacesTest extends BaseJettyTest {

    private int port1;
    private int port2;

    @Test
    public void testProxy() throws Exception {
        
        // give Jetty time to startup properly
        Thread.sleep(2000);

        String reply = template.requestBody("jetty:http://127.0.0.1:" + port1 + "/foo bar", "World", String.class);
        assertEquals("Bye World", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                from("jetty:http://127.0.0.1:" + port1 + "/foo bar?matchOnUriPrefix=true")
                    .to("jetty:http://127.0.0.1:" + port2 + "/proxy bar?bridgeEndpoint=true&throwExceptionOnFailure=false");

                from("jetty:http://127.0.0.1:" + port2 + "/proxy bar?matchOnUriPrefix=true")
                    .transform().simple("Bye ${body}");
            }
        };
    }
}
