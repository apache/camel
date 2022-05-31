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
package org.apache.camel.component.aries;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aries.processor.ConnectionProcessor;
import org.apache.camel.component.aries.processor.ConnectionProcessor.ConnectionResult;
import org.hyperledger.aries.api.connection.ConnectionState;
import org.hyperledger.aries.api.connection.CreateInvitationRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.apache.camel.component.aries.processor.ConnectionProcessor.awaitConnected;
import static org.hyperledger.aries.api.ledger.IndyLedgerRoles.ENDORSER;

/**
 * docker compose up --detach && docker compose logs -f acapy
 */
@EnabledIfSystemProperty(named = "enable.hyperledger.aries.itests", matches = "true",
                         disabledReason = "Requires distributed ledger (i.e. blockchain)")
public class PeerConnectionTest extends AbstractCamelAriesTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:faber-alice-connect")

                        // Faber creates the connection Invitation
                        .to("hyperledger-aries:faber?service=/connections/create-invitation")

                        // Transform CreateInvitationResponse => ConnectionInvitation
                        .transform(simple("${body.invitation}"))

                        // Set an additional message header for Inivitation auto accept
                        // We could also have done this when initiating the route
                        .setHeader("ConnectionReceiveInvitationFilter", () -> Map.of("auto_accept", true))

                        // Setup WebSocket event handling for the Inviter/Invitee
                        .process(new ConnectionProcessor(FABER, ALICE))

                        // Alice consumes the Invitation
                        .to("hyperledger-aries:alice?service=/connections/receive-invitation")

                        // Await connection ACTIVE for the Inviter/Invitee
                        .process(ex -> awaitConnected(ex, 10, TimeUnit.SECONDS));
            }
        };
    }

    @Test
    public void testWorkflow() throws Exception {

        setRemoveWalletsOnShutdown(true);

        onboardWallet(FABER, ENDORSER);
        onboardWallet(ALICE);

        CreateInvitationRequest reqBody = CreateInvitationRequest.builder().build();

        ConnectionResult resObj = template.requestBody("direct:faber-alice-connect", reqBody, ConnectionResult.class);

        log.info("Inviter: [{}] {}", resObj.inviterConnection.getState(), resObj.inviterConnection);
        log.info("Invitee: [{}] {}", resObj.inviteeConnection.getState(), resObj.inviteeConnection);

        Assertions.assertEquals(ConnectionState.ACTIVE, resObj.inviterConnection.getState());
        Assertions.assertEquals(ConnectionState.ACTIVE, resObj.inviteeConnection.getState());
    }
}
