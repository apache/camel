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
package org.apache.camel.component.netty4;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jsse.ClientAuthentication;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.SSLContextServerParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.junit.Test;

public class NettySSLContextParametersTest extends BaseNettyTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("changeit");
        
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);
        
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        // NOTE: Needed since the client uses a loose trust configuration when no ssl context
        // is provided.  We turn on WANT client-auth to prefer using authentication
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setClientAuthentication(ClientAuthentication.WANT.name());

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);

        JndiRegistry registry = super.createRegistry();
        registry.bind("sslContextParameters", sslContextParameters);
        return registry;
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        // ibm jdks dont have sun security algorithms
        if (isJavaVendor("ibm")) {
            return;
        }

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty4:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#sslContextParameters")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.");                           
                        }
                    });
            }
        });
        context.start();

        String response = template.requestBody(
                "netty4:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#sslContextParameters",
                "Epitaph in Kohima, India marking the WWII Battle of Kohima and Imphal, Burma Campaign - Attributed to John Maxwell Edmonds", String.class);
        assertEquals("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.", response);
    }

}
