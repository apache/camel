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

import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;

public class HttpsRouteSslContextParametersInUriTest extends HttpsRouteTest {
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.ks").toString());
        ksp.setPassword(pwd);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(pwd);
        kmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);

        JndiRegistry registry = super.createRegistry();
        registry.bind("sslContextParameters", sslContextParameters);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws URISyntaxException {
                // create jetty component
                JettyHttpComponent jetty = new JettyHttpComponent();
                
                // NOTE: These are here to check that they are properly ignored.
                jetty.addSslSocketConnectorProperty("keyPassword", "sadfasdfasdfas");
                jetty.addSslSocketConnectorProperty("password", "asdfasdfasdfdasfs");
                jetty.addSslSocketConnectorProperty("keystore", "");
                jetty.addSslSocketConnectorProperty("truststoreType", "JKS");

                // add jetty to camel context
                context.addComponent("jetty", jetty);

                from("jetty:https://localhost:" + port1 + "/test?sslContextParametersRef=sslContextParameters").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:https://localhost:" + port1 + "/hello?sslContextParametersRef=sslContextParameters").process(proc);

                from("jetty:https://localhost:" + port2 + "/test?sslContextParametersRef=sslContextParameters").to("mock:b");
            }
        };
    }
}