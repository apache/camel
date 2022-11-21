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

import java.io.IOException;
import java.util.Map;

import org.hyperledger.acy_py.generated.model.DID;
import org.hyperledger.acy_py.generated.model.DIDEndpoint;
import org.hyperledger.aries.api.present_proof.PresentProofRequest;
import org.hyperledger.aries.api.present_proof.PresentProofRequest.ProofRequest;
import org.hyperledger.aries.api.present_proof.PresentProofRequest.ProofRequest.ProofRequestedAttributes;
import org.hyperledger.aries.api.present_proof.PresentationExchangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.hyperledger.aries.api.ledger.IndyLedgerRoles.ENDORSER;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeRole.VERIFIER;
import static org.hyperledger.aries.api.present_proof.PresentationExchangeState.REQUEST_SENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * docker compose up --detach && docker compose logs -f acapy
 */
@EnabledIfSystemProperty(named = "enable.hyperledger.aries.itests", matches = "true",
                         disabledReason = "Requires distributed ledger (i.e. blockchain)")
public class ConnectionlessProofTest extends AbstractCamelAriesTest {

    @BeforeEach
    void beforeEach() throws IOException {
        setRemoveWalletsOnShutdown(true);
        onboardWallet(FABER, ENDORSER);
    }

    /**
     * Creating proof request without connection ID.
     */
    @Test
    void shouldCreateProofRequest() {
        var request = new PresentProofRequest();
        var proofRequest = new ProofRequest();
        var attribute = new ProofRequestedAttributes();
        attribute.setName("first_name");
        proofRequest.setRequestedAttributes(Map.of("0_first_name_uuid", attribute));
        proofRequest.setRequestedPredicates(Map.of());
        request.setProofRequest(proofRequest);

        var result = template.requestBody(
                "hyperledger-aries:faber?service=/present-proof/create-request",
                request,
                PresentationExchangeRecord.class);

        assertNotNull(result.getPresentationExchangeId());
        assertEquals(REQUEST_SENT, result.getState());
        assertEquals(VERIFIER, result.getRole());
    }

    /**
     * This endpoint is required to add service decorator to connectionless proof presentation.
     */
    @Test
    void shouldReturnPublicDidEndpoint() {
        var did = template.requestBody(
                "hyperledger-aries:faber?service=/wallet/did/public",
                null,
                DID.class);

        var result = template.requestBodyAndHeader(
                "hyperledger-aries:faber?service=/wallet/get-did-endpoint",
                null,
                "did",
                did.getDid(),
                DIDEndpoint.class);

        assertNotNull(result.getEndpoint());
        assertEquals(did.getDid(), result.getDid());
    }
}
