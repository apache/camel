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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.IOHelper;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.junit.Test;

public class HandlerTest extends BaseJettyTest {
    private StatisticsHandler statisticsHandler1 = new StatisticsHandler();
    private StatisticsHandler statisticsHandler2 = new StatisticsHandler();
    private StatisticsHandler statisticsHandler3 = new StatisticsHandler();

    private String htmlResponse = "<html><body>Book 123 is Camel in Action</body></html>";
    private int port1;
    private int port2;

    @Test
    public void testWithOneHandler() throws Exception {
        // First test the situation where one should invoke the handler once
        assertEquals(0, statisticsHandler1.getRequests());
        assertEquals(0, statisticsHandler2.getRequests());
        assertEquals(0, statisticsHandler3.getRequests());
        
        InputStream html = (InputStream) template.requestBody("http://localhost:" + port1, "");
        BufferedReader br = IOHelper.buffered(new InputStreamReader(html));
        
        assertEquals(htmlResponse, br.readLine());
        assertEquals(1, statisticsHandler1.getRequests());
        assertEquals(0, statisticsHandler2.getRequests());
        assertEquals(0, statisticsHandler3.getRequests());
    }
    
    @Test
    public void testWithTwoHandlers() throws Exception {
        // First test the situation where one should invoke the handler once
        assertEquals(0, statisticsHandler1.getRequests());
        assertEquals(0, statisticsHandler2.getRequests());
        assertEquals(0, statisticsHandler3.getRequests());

        InputStream html = (InputStream) template.requestBody("http://localhost:" + port2, "");
        BufferedReader br = IOHelper.buffered(new InputStreamReader(html));
        
        assertEquals(htmlResponse, br.readLine());
        assertEquals(0, statisticsHandler1.getRequests());
        assertEquals(1, statisticsHandler2.getRequests());
        assertEquals(1, statisticsHandler3.getRequests());
    }

    @Test
    public void testWithTwoHandlersTwoEndpointsOnSamePort() throws Exception {
        // First test the situation where one should invoke the handler once
        assertEquals(0, statisticsHandler1.getRequests());
        assertEquals(0, statisticsHandler2.getRequests());
        assertEquals(0, statisticsHandler3.getRequests());

        InputStream html1 = (InputStream) template.requestBody("http://localhost:" + port2, "");
        BufferedReader br1 = IOHelper.buffered(new InputStreamReader(html1));
        assertEquals(htmlResponse, br1.readLine());
        
        InputStream html2 = (InputStream) template.requestBody("http://localhost:" + port2 + "/endpoint2", "");
        BufferedReader br2 = IOHelper.buffered(new InputStreamReader(html2));
        assertEquals(htmlResponse, br2.readLine());
        
        assertEquals(0, statisticsHandler1.getRequests());
        assertEquals(2, statisticsHandler2.getRequests());
        assertEquals(2, statisticsHandler3.getRequests());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("statisticsHandler1", statisticsHandler1);
        jndi.bind("statisticsHandler2", statisticsHandler2);
        jndi.bind("statisticsHandler3", statisticsHandler3);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                from("jetty:http://localhost:" + port1 + "/?handlers=#statisticsHandler1")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody(htmlResponse);
                            }
                        });

                from("jetty:http://localhost:" + port2 + "/?handlers=#statisticsHandler2,#statisticsHandler3")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody(htmlResponse);
                            }
                        });
                from("jetty:http://localhost:" + port2 + "/endpoint2?handlers=#statisticsHandler2,#statisticsHandler3")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody(htmlResponse);
                            }
                        });
            };
        };
    }
}