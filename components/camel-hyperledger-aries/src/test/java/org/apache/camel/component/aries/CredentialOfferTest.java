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
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.hyperledger.aries.api.credentials.CredentialAttributes;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeRole.ISSUER;
import static org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeState.OFFER_SENT;
import static org.hyperledger.aries.api.ledger.IndyLedgerRoles.ENDORSER;

/**
 * docker compose up --detach && docker compose logs -f acapy
 */
@EnabledIfSystemProperty(named = "enable.hyperledger.aries.itests", matches = "true",
                         disabledReason = "Requires distributed ledger (i.e. blockchain)")
public class CredentialOfferTest extends AbstractCamelAriesTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {

                BiFunction<Exchange, String, Object> simple
                        = (ex, exp) -> new ValueBuilder(simple(exp)).evaluate(ex, Object.class);

                @SuppressWarnings("unchecked")
                BiFunction<Exchange, String, List<CredentialAttributes>> attributes
                        = (ex, exp) -> CredentialAttributes.from(new ValueBuilder(simple(exp)).evaluate(ex, Map.class));

                // Faber creates a Transscript Credential Definition
                // Note, the Schema is created on-demand
                // Then, Faber creates Transcript Credential Offer
                from("direct:start")
                        .setHeader("Payload", simple("${body}"))
                        .setBody(simple("${header.payload[schema]}"))
                        .to("hyperledger-aries:faber?service=/credential-definitions&schemaName=Transscript&autoSchema=true")
                        .setHeader("CredentialDefinitionId", simple("${body.credentialDefinitionId}"))
                        .to("direct:transscript-credential-offer");

                // Faber creates Transcript Credential Offer
                from("direct:transscript-credential-offer")
                        .setBody(ex -> Map.of(
                                "cred_def_id", simple.apply(ex, "${header.credentialDefinitionId}"),
                                "credential_preview", Map.of("attributes", attributes.apply(ex, "${header.payload[offer]}"))))
                        .to("hyperledger-aries:faber?service=/issue-credential/create-offer");
            }
        };
    }

    @Test
    public void testWorkflow() throws Exception {

        setRemoveWalletsOnShutdown(true);

        onboardWallet(FABER, ENDORSER);

        Map<String, Object> reqBody = Map.of(
                "schema", Map.of(
                        "schemaVersion", "1.2",
                        "attributes", Arrays.asList("first_name", "last_name", "ssn", "degree", "status", "year", "average"),
                        "supportRevocation", "false"),
                "offer", Map.of(
                        "first_name", "Alice",
                        "last_name", "Garcia",
                        "ssn", "123-45-6789",
                        "degree", "Bachelor of Science, Marketing",
                        "status", "graduated",
                        "year", "2015",
                        "average", "5"));

        V1CredentialExchange resObj = template.requestBody("direct:start", reqBody, V1CredentialExchange.class);
        Assertions.assertEquals(ISSUER, resObj.getRole());
        Assertions.assertEquals(OFFER_SENT, resObj.getState());
    }
}
