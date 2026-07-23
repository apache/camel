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
package org.apache.camel.component.milo;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.milo.NodeIds.nodeValue;

/**
 * Verifies that explicit username/password endpoint parameters work correctly, especially when credentials contain
 * special characters (such as {@code ?}, {@code &}, {@code @}) that would break URI-embedded credentials.
 */
public class ExplicitCredentialsTest extends AbstractMiloServerTest {

    private static final String MILO_SERVER_ITEM = "milo-server:myitem1";

    // Use explicit username/password parameters instead of embedding in URI
    private static final String MILO_CLIENT_ITEM
            = "milo-client:opc.tcp://localhost:@@port@@?node="
              + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1")
              + "&overrideHost=true&allowedSecurityPolicies=None"
              + "&username=RAW(" + SPECIAL_CHAR_USER + ")"
              + "&password=RAW(" + SPECIAL_CHAR_CREDENTIAL + ")";

    private static final String MOCK_RESULT = "mock:complex_credentials_result";

    @Override
    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        server.setBindAddresses("localhost");
        server.setPort(getServerPort());
        // register credentials with special characters
        server.setUserAuthenticationCredentials(SPECIAL_CHAR_USER + ":" + SPECIAL_CHAR_CREDENTIAL);
        server.setUsernameSecurityPolicyUri(SecurityPolicy.None);
        server.setSecurityPoliciesById("None");
        server.setEnableAnonymousAuthentication(false);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(MILO_SERVER_ITEM).to(MOCK_RESULT);
                from("direct:start").to(resolve(MILO_CLIENT_ITEM));
            }
        };
    }

    @Test
    void shouldAuthenticateWithExplicitCredentialsContainingSpecialCharacters() throws Exception {

        final String testBody = "test_special_chars";

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);
        testBody(mock.message(0), assertGoodValue(testBody));

        // write synchronously so the server receives the value before assertion
        template.sendBodyAndHeader("direct:start", new Variant(testBody), "CamelMiloAwait", true);

        // verify that the message sent via the client was received by the server endpoint
        mock.assertIsSatisfied();
    }
}
