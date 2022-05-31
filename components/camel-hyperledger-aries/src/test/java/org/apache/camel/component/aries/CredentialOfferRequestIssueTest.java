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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aries.processor.ConnectionProcessor;
import org.apache.camel.component.aries.processor.V1CredentialEventProcessor;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.credentials.CredentialAttributes;
import org.hyperledger.aries.api.credentials.CredentialPreview;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialOfferRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.apache.camel.component.aries.processor.ConnectionProcessor.awaitConnected;
import static org.apache.camel.component.aries.processor.V1CredentialEventProcessor.awaitHolderCredentialAcked;
import static org.apache.camel.component.aries.processor.V1CredentialEventProcessor.awaitHolderCredentialReceived;
import static org.apache.camel.component.aries.processor.V1CredentialEventProcessor.awaitHolderOfferReceived;
import static org.apache.camel.component.aries.processor.V1CredentialEventProcessor.awaitIssuerRequestReceived;
import static org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeRole.HOLDER;
import static org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeState.CREDENTIAL_ACKED;
import static org.hyperledger.aries.api.ledger.IndyLedgerRoles.ENDORSER;

/**
 * docker compose up --detach && docker compose logs -f acapy
 */
@EnabledIfSystemProperty(named = "enable.hyperledger.aries.itests", matches = "true",
                         disabledReason = "Requires distributed ledger (i.e. blockchain)")
public class CredentialOfferRequestIssueTest extends AbstractCamelAriesTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // Faber creates a Transscript Credential Definition
                // Note, the Schema is created on-demand
                from("direct:transscript-credential-definition")
                        .to("hyperledger-aries:faber?service=/credential-definitions&schemaName=Transscript&autoSchema=true")
                        .setHeader("CredentialDefinitionId", simple("${body.credentialDefinitionId}"))
                        .to("direct:faber-alice-connect");

                // Faber connects to Alice
                from("direct:faber-alice-connect")
                        .to("hyperledger-aries:faber?service=/connections/create-invitation")
                        .transform(simple("${body.invitation}"))
                        .setHeader("ConnectionReceiveInvitationFilter", () -> Map.of("auto_accept", true))
                        .process(new ConnectionProcessor(FABER, ALICE))
                        .to("hyperledger-aries:alice?service=/connections/receive-invitation")
                        .process(ex -> awaitConnected(ex, 10, TimeUnit.SECONDS))
                        .to("direct:transscript-credential-offer");

                // Faber sends the Transcript Credential Offer
                from("direct:transscript-credential-offer")
                        .setBody(ex -> V1CredentialOfferRequest.builder()
                                .connectionId(ex.getIn().getHeader("FaberAliceConnectionRecord", ConnectionRecord.class)
                                        .getConnectionId())
                                .credentialDefinitionId(ex.getIn().getHeader("CredentialDefinitionId", String.class))
                                .credentialPreview(new CredentialPreview(
                                        CredentialAttributes.from(Map.of(
                                                "first_name", "Alice",
                                                "last_name", "Garcia",
                                                "ssn", "123-45-6789",
                                                "degree", "Bachelor of Science, Marketing",
                                                "status", "graduated",
                                                "year", "2015",
                                                "average", "5"))))
                                .build())
                        .process(new V1CredentialEventProcessor(FABER, ALICE))
                        .to("hyperledger-aries:faber?service=/issue-credential/send-offer")
                        .to("direct:send-credential-request");

                // Faber issues the Transcript Credential
                from("direct:send-credential-request")
                        .process(ex -> awaitHolderOfferReceived(ex, 10, TimeUnit.SECONDS))
                        .toD("hyperledger-aries:alice?service=/issue-credential/records/${header.holderCredentialExchange.credentialExchangeId}/send-request")
                        .process(ex -> awaitIssuerRequestReceived(ex, 10, TimeUnit.SECONDS))
                        .toD("hyperledger-aries:faber?service=/issue-credential/records/${header.issuerCredentialExchange.credentialExchangeId}/issue")
                        .process(ex -> awaitHolderCredentialReceived(ex, 10, TimeUnit.SECONDS))
                        .toD("hyperledger-aries:alice?service=/issue-credential/records/${header.holderCredentialExchange.credentialExchangeId}/store")
                        .process(ex -> awaitHolderCredentialAcked(ex, 10, TimeUnit.SECONDS));
            }
        };
    }

    @Test
    public void testWorkflow() throws Exception {

        setRemoveWalletsOnShutdown(true);

        onboardWallet(FABER, ENDORSER);
        onboardWallet(ALICE);

        Map<String, Object> reqBody = Map.of(
                "schemaVersion", "1.2",
                "attributes", Arrays.asList("first_name", "last_name", "ssn", "degree", "status", "year", "average"),
                "supportRevocation", "false");

        V1CredentialExchange resObj
                = template.requestBody("direct:transscript-credential-definition", reqBody, V1CredentialExchange.class);
        Assertions.assertEquals(HOLDER, resObj.getRole());
        Assertions.assertEquals(CREDENTIAL_ACKED, resObj.getState());
    }
}
