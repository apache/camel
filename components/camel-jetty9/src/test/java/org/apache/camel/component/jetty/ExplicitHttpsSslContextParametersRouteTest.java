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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.server.Connector;
import org.junit.Ignore;

@Ignore
public class ExplicitHttpsSslContextParametersRouteTest extends HttpsRouteTest {

    // START SNIPPET: e2
    private Connector createSslSocketConnector(CamelContext context, int port) throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.ks").toString());
        ksp.setPassword(pwd);
        
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(pwd);
        kmp.setKeyStore(ksp);
        
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        
        // From Camel 2.5.0 Camel-Jetty is using SslSelectChannelConnector instead of SslSocketConnector
        //SslSelectChannelConnector sslSocketConnector = new SslSelectChannelConnector();
        //sslSocketConnector.getSslContextFactory().setSslContext(sslContextParameters.createSSLContext());
        //sslSocketConnector.setPort(port);
        
        //return sslSocketConnector;
        return null;
    }
    // END SNIPPET: e2

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // create SSL select channel connectors for port 9080 and 9090
                Map<Integer, Connector> connectors = new HashMap<Integer, Connector>();
                connectors.put(port1, createSslSocketConnector(getContext(), port1));
                connectors.put(port2, createSslSocketConnector(getContext(), port2));

                JettyHttpComponent jetty = getContext().getComponent("jetty", JettyHttpComponent.class);
                jetty.setSslSocketConnectors(connectors);
                // END SNIPPET: e1

                from("jetty:https://localhost:" + port1 + "/test").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:https://localhost:" + port1 + "/hello").process(proc);
                
                from("jetty:https://localhost:" + port2 + "/test").to("mock:b");
            }
        };
    }
}