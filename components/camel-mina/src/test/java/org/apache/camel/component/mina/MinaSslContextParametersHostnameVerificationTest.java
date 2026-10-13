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
package org.apache.camel.component.mina;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The producer checks that the certificate of the server matches the host it connects to.
 * <p>
 * The certificate in jsse/localhost.p12 lists only DNS:localhost as subject alternative name, so it matches "localhost"
 * but not "127.0.0.1".
 */
class MinaSslContextParametersHostnameVerificationTest extends BaseMinaTest {

    @Test
    void sendsWhenTheCertificateMatchesTheHost() {
        Object reply = template.requestBody(producerUri("localhost", ""), "Hello");

        assertEquals("Bye Hello", reply);
    }

    @Test
    void failsWhenTheCertificateDoesNotMatchTheHost() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        // MINA keeps the handshake error in its SslHandler, so the producer fails when the write does not complete:
        // short timeouts keep the test fast
        assertThrows(CamelExecutionException.class,
                () -> template.requestBody(producerUri("127.0.0.1", "&writeTimeout=2000&timeout=2000"), "Hello"));

        result.assertIsSatisfied();
    }

    @Test
    void sendsToAHostTheCertificateDoesNotListWhenHostnameVerificationIsDisabled() {
        Object reply = template.requestBody(producerUri("127.0.0.1", "&hostnameVerification=false"), "Hello");

        assertEquals("Bye Hello", reply);
    }

    private String producerUri(String host, String options) {
        return "mina:tcp://" + host + ":" + getPort() + "?sync=true&textline=true&sslContextParameters=#sslContextParameters"
               + options;
    }

    @Override
    protected boolean isUseSslContext() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // 0.0.0.0, so the producer can connect with a host name or with an IP address
                fromF("mina:tcp://0.0.0.0:%d?sync=true&textline=true&sslContextParameters=#sslContextParameters", getPort())
                        .to("mock:result")
                        .transform(body().prepend("Bye "));
            }
        };
    }
}
