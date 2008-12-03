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
package org.apache.camel.component.cometd;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class CometdProducerConsumerInteractiveTest {

    private static final String URI = "cometd://localhost:8080/service/test?resourceBase=./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private static final String URIS = "cometds://localhost:8443/service/test?resourceBase=./src/test/resources/webapp&"
        + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private CamelContext context;

    private String pwd = "changeit";
    
    public static void main(String[] args) throws Exception {
        CometdProducerConsumerInteractiveTest me = new CometdProducerConsumerInteractiveTest();
        me.testCometdProducerConsumerInteractive();
    }

    public void testCometdProducerConsumerInteractive() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        context.start();
    }

    private RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                CometdComponent component = (CometdComponent) context.getComponent("cometds");
                component.setSslPassword(pwd);
                component.setSslKeyPassword(pwd);
                URI keyStoreUrl = null;
                File file = new File("./src/test/resources/jsse/localhost.ks");
                keyStoreUrl = file.toURI();
                component.setSslKeystore(keyStoreUrl.getPath());
                                
                from("stream:in").to(URI).to(URIS);
            }
        };
    }

}
