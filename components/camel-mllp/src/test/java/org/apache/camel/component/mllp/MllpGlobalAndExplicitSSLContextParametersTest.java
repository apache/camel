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
package org.apache.camel.component.mllp;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MllpGlobalAndExplicitSSLContextParametersTest extends CamelTestSupport {

    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock:result")
    MockEndpoint result;

    private SSLContextParameters createGlobalSslContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    private SSLContextParameters createExplicitSslContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        // Set global SSLContextParameters
        SSLContextParameters globalSslContextParameters = createGlobalSslContextParameters();
        context.setSSLContextParameters(globalSslContextParameters);

        // Bind explicit SSLContextParameters for the receiving endpoint
        SSLContextParameters explicitSslContextParameters = createExplicitSslContextParameters();
        context.getRegistry().bind("explicitSslContextParameters", explicitSslContextParameters);
        ((SSLContextParametersAware) context.getComponent("mllp")).setUseGlobalSslContextParameters(true);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Receiving endpoint explicitly defines SSLContextParameters
                fromF("mllp://%s:%d?sslContextParameters=#explicitSslContextParameters", mllpClient.getMllpHost(),
                        mllpClient.getMllpPort())
                        .log(LoggingLevel.INFO, "Received Message: ${body}")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testGlobalAndExplicitSslContextParameters() throws Exception {
        String hl7Message = "MSH|^~\\&|CLIENT|TEST|SERVER|ACK|20231118120000||ADT^A01|123456|T|2.6\r" +
                            "EVN|A01|20231118120000\r" +
                            "PID|1|12345|67890||DOE^JOHN||19800101|M|||123 Main St^^Springfield^IL^62704||(555)555-5555|||||S\r"
                            +
                            "PV1|1|O\r";

        result.expectedBodiesReceived(hl7Message);

        String endpointUri = String.format("mllp://%s:%d", mllpClient.getMllpHost(), mllpClient.getMllpPort());
        template.sendBody(endpointUri, hl7Message);
        //        template.sendBody("direct:start", hl7Message);

        result.assertIsSatisfied();
    }
}
