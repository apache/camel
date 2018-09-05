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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;

public class JettyHttpsProducerSslContextInUriTest extends JettyProducerHttpsRouteTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.ks").toString());
        ksp.setPassword(pwd);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(pwd);
        kmp.setKeyStore(ksp);

        //TrustManagersParameters tmp = new TrustManagersParameters();
        //tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        //sslContextParameters.setTrustManagers(tmp);

        JndiRegistry registry = super.createRegistry();
        registry.bind("sslContextParameters", sslContextParameters);

        return registry;
    }
    
    protected void invokeHttpEndpoint() throws IOException {
        template.sendBodyAndHeader(getHttpProducerScheme() + "localhost:" + port1 + "/test?sslContextParameters=#sslContextParameters", expectedBody, "Content-Type",
                                   "application/xml");
        template.sendBodyAndHeader(getHttpProducerScheme() + "localhost:" + port2 + "/test?sslContextParameters=#sslContextParameters", expectedBody, "Content-Type",
                                   "application/xml");
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws URISyntaxException {
                JettyHttpComponent componentJetty = (JettyHttpComponent) context.getComponent("jetty");
                componentJetty.setSslPassword(pwd);
                componentJetty.setSslKeyPassword(pwd);
                URL keyStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.ks");
                componentJetty.setKeystore(keyStoreUrl.toURI().getPath());
                
                from("jetty:https://localhost:" + port1 + "/test?sslContextParameters=#sslContextParameters").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:https://localhost:" + port1 + "/hello?sslContextParameters=#sslContextParameters").process(proc);
                
                from("jetty:https://localhost:" + port2 + "/test?sslContextParameters=#sslContextParameters").to("mock:b");
            }
        };
    }
}
