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
package org.apache.camel.component.netty;

import java.io.File;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
public class NettySSLClientCertHeadersTest extends BaseNettyTest {

    @BindToRegistry("ksf")
    public File loadKeystoreKsf() {
        return new File("src/test/resources/keystore.jks");
    }

    @BindToRegistry("tsf")
    public File loadKeystoreTsf() {
        return new File("src/test/resources/keystore.jks");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(1);

        getMockEndpoint("mock:input").expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME,
                "SERIALNUMBER=1234567890, CN=CommonName, OU=Unit, O=Org, L=Loc, ST=State, C=US");
        getMockEndpoint("mock:input").expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_ISSUER_NAME,
                "SERIALNUMBER=1234567890, CN=CommonName, OU=Unit, O=Org, L=Loc, ST=State, C=US");
        getMockEndpoint("mock:input").expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO, "1234567890");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                // needClientAuth=true so we can get the client certificate
                // details
                from("netty:tcp://localhost:{{port}}?sync=true&ssl=true&passphrase=changeit&keyStoreResource=#ksf&trustStoreResource=#tsf"
                     + "&needClientAuth=true&sslClientCertHeaders=true").to("mock:input").transform().constant("Bye World");
            }
        });
        context.start();

        String response = template.requestBody(
                "netty:tcp://localhost:{{port}}?sync=true&ssl=true&passphrase=changeit&keyStoreResource=#ksf&trustStoreResource=#tsf",
                "Hello World", String.class);
        assertEquals("Bye World", response);

        MockEndpoint.assertIsSatisfied(context);
    }

}
