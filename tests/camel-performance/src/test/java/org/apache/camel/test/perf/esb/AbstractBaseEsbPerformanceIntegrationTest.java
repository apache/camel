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
package org.apache.camel.test.perf.esb;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;

public abstract class AbstractBaseEsbPerformanceIntegrationTest extends CamelBlueprintTestSupport {

    protected Server server;
    protected String payload;
    protected int count = 10000;

    @Before
    @Override
    public void setUp() throws Exception {
        server = new Server(9000);

        ServerConnector connector0 = new ServerConnector(server);
        connector0.setReuseAddress(true);
        server.setConnectors(new Connector[]{connector0});

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/service");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new EchoService()), "/EchoService");
        server.start();

        payload = readPayload();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        
        server.stop();
    }

    protected String readPayload() throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream("/1K_buyStocks.xml"));
    }

    protected void send(String endpointUri, int messagesToSend) {
        template.setDefaultEndpointUri(endpointUri);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("Content-Type", "text/xml;charset=UTF-8");
        headers.put("SOAPAction", "urn:buyStocks.2");
        headers.put("routing", "xadmin;server1;community#1.0##");

        for (int i = 0; i < messagesToSend; i++) {
            template.requestBodyAndHeaders(payload, headers);
        }
    }
}
