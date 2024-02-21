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
package org.apache.camel.component.vertx.websocket;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.Main;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.junit.jupiter.api.Test;

public class MainVertxWebsocketSSLTest extends VertxWebSocketTestSupport {

    @Test
    public void testGlobalServerSSLContextParameters() throws Exception {

        Main main = new Main();
        main.configure().sslConfig().setEnabled(true);
        main.configure().sslConfig().setKeyStore("server.jks");
        main.configure().sslConfig().setKeystorePassword("security");
        main.configure().sslConfig().setTrustStore("client.jks");
        main.configure().sslConfig().setTrustStorePassword("storepass");
        main.configure().sslConfig().setClientAuthentication(ClientAuthentication.REQUIRE.name());
        main.addProperty("camel.component.vertx-websocket.useglobalsslcontextparameters", "true");

        main.configure().addRoutesBuilder(new RouteBuilder() {

            public void configure() {
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/echo?", port);

                fromF("vertx-websocket:localhost:%d/echo?", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");
            }
        });

        main.start();
        try {
            MockEndpoint mockEndpoint = main.getCamelContext().getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello world");

            main.getCamelTemplate().sendBody("direct:start", "world");

            mockEndpoint.assertIsSatisfied();
        } finally {
            main.stop();
        }
    }

    @Override
    protected void startCamelContext() {
    }
}
