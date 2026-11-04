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
package org.apache.camel.component.cyberark.vault;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * getSecret sets the secret only as the body, so a createSecret later in the same exchange stores its own body.
 */
class CyberArkVaultProducerTest extends CamelTestSupport {

    private static final String SECRET_ID = "db/password";

    private final ConjurClient client = mock(ConjurClient.class);

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        when(client.retrieveSecret(SECRET_ID, null)).thenReturn("old-value");
        registry.bind("conjurClient", client);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:get")
                        .to("cyberark-vault:secret?secretId=" + SECRET_ID + "&conjurClient=#conjurClient")
                        .to("mock:result");

                from("direct:getThenCreate")
                        .to("cyberark-vault:secret?secretId=" + SECRET_ID + "&conjurClient=#conjurClient")
                        .setBody(constant("new-value"))
                        .to("cyberark-vault:secret?operation=createSecret&secretId=" + SECRET_ID
                            + "&conjurClient=#conjurClient")
                        .to("mock:result");

                from("direct:createFromHeader")
                        .setHeader(CyberArkVaultConstants.SECRET_VALUE, constant("header-value"))
                        .to("cyberark-vault:secret?operation=createSecret&secretId=" + SECRET_ID
                            + "&conjurClient=#conjurClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void getSecretSetsTheSecretOnlyAsTheBody() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("old-value");

        template.sendBody("direct:get", null);

        result.assertIsSatisfied();
        Exchange exchange = result.getExchanges().get(0);
        assertNull(exchange.getMessage().getHeader(CyberArkVaultConstants.SECRET_VALUE));
        assertEquals(SECRET_ID, exchange.getMessage().getHeader(CyberArkVaultConstants.SECRET_ID));
    }

    @Test
    void createSecretAfterGetSecretStoresTheNewBody() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("new-value");

        template.sendBody("direct:getThenCreate", null);

        result.assertIsSatisfied();
        verify(client).createSecret(SECRET_ID, "new-value");
    }

    @Test
    void createSecretStoresTheSecretValueHeader() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody("direct:createFromHeader", "body-value");

        result.assertIsSatisfied();
        verify(client).createSecret(SECRET_ID, "header-value");
    }
}
