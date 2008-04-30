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

import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.mortbay.jetty.security.SslSocketConnector;

public class ExplicitHttpsRouteTest extends HttpsRouteTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                SslSocketConnector sslSocketConnector = new SslSocketConnector();
                sslSocketConnector.setKeyPassword(pwd);
                sslSocketConnector.setPassword(pwd);
                URL keyStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.ks");
                sslSocketConnector.setKeystore(keyStoreUrl.getPath());
                sslSocketConnector.setTruststoreType("JKS");
                
                JettyHttpComponent componentJetty = (JettyHttpComponent) context.getComponent("jetty");
                componentJetty.setSslSocketConnector(sslSocketConnector);
                
                from("jetty:https://localhost:8080/test").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut(true).setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:https://localhost:8080/hello").process(proc);
            }
        };
    }
}

