/*
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
package org.apache.camel.component.restlet;

import java.io.File;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.Test;

public class RestletProducerSSLTest extends RestletTestSupport {

    public static final String BODY = "some body";

    @BindToRegistry("mySSLContextParameters")
    private SSLContextParameters sslContextParameters = createSSLContext();

    @Test
    public void testRestletProducerComponentSSL() throws Exception {
        String out = template.requestBody("direct:startWithEndpointSSL", "neco", String.class);
        assertEquals(BODY, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startWithEndpointSSL").to("restlet:https://localhost:" + portNum + "/?restletMethod=post&sslContextParameters=#mySSLContextParameters");

                from("restlet:https://localhost:" + portNum + "/?restletMethods=post&sslContextParameters=#mySSLContextParameters").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(BODY);
                    }
                });
            }
        };
    }

    protected SSLContextParameters createSSLContext() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(new File("src/test/resources/jsse/localhost.ks").getAbsolutePath());
        ksp.setPassword("changeit");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }
}
