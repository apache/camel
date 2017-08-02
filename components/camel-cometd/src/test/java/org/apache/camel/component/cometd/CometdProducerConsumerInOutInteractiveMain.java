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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Ignore;

@Ignore("Run this test manually")
public class CometdProducerConsumerInOutInteractiveMain {

    private static final String URI = "cometd://127.0.0.1:9091/service/test?baseResource=file:./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private static final String URIS = "cometds://127.0.0.1:9443/service/test?baseResource=file:./src/test/resources/webapp&"
        + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private CamelContext context;

    private String pwd = "changeit";

    public static void main(String[] args) throws Exception {
        CometdProducerConsumerInOutInteractiveMain me = new CometdProducerConsumerInOutInteractiveMain();
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
                File file = new File("./src/test/resources/jsse/localhost.ks");
                URI keyStoreUrl = file.toURI();
                component.setSslKeystore(keyStoreUrl.getPath());

                from(URI, URIS).setExchangePattern(ExchangePattern.InOut).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message out = new DefaultMessage(exchange.getContext());
                        out.setBody("reply: " + exchange.getIn().getBody());
                        exchange.setOut(out);
                    }
                });
            }
        };
    }

}
