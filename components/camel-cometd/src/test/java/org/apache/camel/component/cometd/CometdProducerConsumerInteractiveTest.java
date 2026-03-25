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
package org.apache.camel.component.cometd;

import java.net.URL;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CometdProducerConsumerInteractiveTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port portSSL = AvailablePortFinder.find();
    private String uri;
    private String uriSSL;

    private final String pwd = "changeit";

    @Test
    void testProducerToPlainAndSslEndpoints() throws Exception {
        getMockEndpoint("mock:plain").expectedBodiesReceived("hello");
        getMockEndpoint("mock:ssl").expectedBodiesReceived("hello");
        template.sendBody("direct:input", "hello");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    public void setupResources() {
        uri = "cometd://127.0.0.1:" + port.getPort() + "/channel/test?baseResource=file:./src/test/resources/webapp&"
              + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

        uriSSL = "cometds://127.0.0.1:" + portSSL.getPort() + "/channel/test?baseResource=file:./src/test/resources/webapp&"
                 + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                CometdComponent sslComponent = context.getComponent("cometds", CometdComponent.class);
                sslComponent.setSslPassword(pwd);
                sslComponent.setSslKeyPassword(pwd);
                URL keyStoreUrl = CometdProducerConsumerInteractiveTest.class.getResource("/jsse/localhost.p12");
                sslComponent.setSslKeystore(keyStoreUrl.getPath());

                from("direct:input").to(uri).to(uriSSL);

                from(uri).to("mock:plain");
                from(uriSSL).to("mock:ssl");
            }
        };
    }
}
