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
package org.apache.camel.component.cxf.jaxrs;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.net.telnet.TelnetClient;
import org.junit.Test;

/**
 * UnitOfWork should complete even if client disconnected during the processing.
 */
public class CxfRsConsumerClientDisconnectedTest extends CamelTestSupport {
    private static final int PORT = CXFTestSupport.getPort1();
    private static final String CONTEXT = "/CxfRsConsumerClientDisconnectedTest";
    private static final String CXT = PORT + CONTEXT;

    private String cxfRsEndpointUri = "cxfrs://http://localhost:" + CXT + "/rest?synchronous=" + isSynchronous()
                                      + "&dataFormat=PAYLOAD&resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            public void configure() {

                getContext().setStreamCaching(true);
                getContext().getStreamCachingStrategy().setSpoolThreshold(1L);
                errorHandler(noErrorHandler());

                Response ok = Response.ok().build();

                from(cxfRsEndpointUri)
                    // should be able to convert to Customer
                    .to("mock:result")
                    .process(exchange-> {
                        Thread.sleep(100);

                        exchange.addOnCompletion(new Synchronization() {
                            @Override
                            public void onComplete(Exchange exchange) {
                                template.sendBody("mock:onComplete", "");
                            }

                            @Override
                            public void onFailure(Exchange exchange) {

                            }
                        });
                    });

            };
        };
    }

    @Test
    public void testClientDisconnect() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        MockEndpoint onComplete = getMockEndpoint("mock:onComplete");
        onComplete.expectedMessageCount(1);

        TelnetClient telnetClient = new TelnetClient();

        telnetClient.connect("localhost", PORT);
        telnetClient.setTcpNoDelay(true);
        telnetClient.setReceiveBufferSize(1);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(telnetClient.getOutputStream()));
        writer.write("GET " + CONTEXT + "/rest/customerservice/customers HTTP/1.1\nhost: localhost\n\n");
        writer.flush();
        telnetClient.disconnect();
        mock.assertIsSatisfied();
        onComplete.assertIsSatisfied();



    }

    protected boolean isSynchronous() {
        return false;
    }

}
