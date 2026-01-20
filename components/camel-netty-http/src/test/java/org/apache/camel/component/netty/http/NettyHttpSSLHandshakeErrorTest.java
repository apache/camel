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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
@DisabledOnOs(architectures = { "s390x" },
              disabledReason = "This test does not run reliably on s390x (see CAMEL-21438)")
public class NettyHttpSSLHandshakeErrorTest extends BaseNettyTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testHttpsHandshakeError() throws Exception {
        getMockEndpoint("mock:target").expectedMessageCount(0);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty-http:https://localhost:{{port}}?ssl=true&needClientAuth=true&keyStoreFormat=JKS"
                     + "&passphrase=storepassword&keyStoreResource=jsse/server-keystore.jks&trustStoreResource=jsse/server-truststore.jks")
                        .to("mock:target");
            }
        });
        context.start();

        DefaultExchange exchange = new DefaultExchange(context);

        Exchange response = template
                .send("netty-http:https://localhost:{{port}}?requestTimeout=10000&throwExceptionOnFailure=false"
                      + "&ssl=true&keyStoreFormat=JKS&passphrase=storepassword&keyStoreResource=jsse/client-keystore.jks&trustStoreResource=jsse/server-truststore.jks",
                        exchange);

        Exception ex = response.getException();

        assertTrue(response.isFailed(), "should have failed");
        assertNotNull(ex);
        assertEquals(javax.net.ssl.SSLHandshakeException.class, ex.getClass(), "SSLHandshakeException expected");

        MockEndpoint.assertIsSatisfied(context);
    }

}
