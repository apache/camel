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
package org.apache.camel.component.as2;

import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests an AS2 server configured to require signature verification ({@code signatureVerificationRequired=true}) but
 * without a {@code validateSigningCertificateChain} to verify against. <br>
 * A signed message cannot be verified in this configuration, so rather than silently delivering the unverified payload
 * the server rejects it with an 'insufficient-message-security' error. <br>
 * Unsigned messages have no signature to verify and are unaffected.
 */
public class AS2ServerSecSignatureRequiredIT extends AS2ServerSecTestBase {

    @Override
    protected void customizeConfiguration(AS2Configuration configuration) {
        super.customizeConfiguration(configuration);
        // Require signature verification, but deliberately leave validateSigningCertificateChain unset so that a
        // signed message cannot be verified.
        configuration.setSignatureVerificationRequired(true);
    }

    // A signed message that cannot be verified (no validation chain configured) must be rejected, not delivered.
    @ParameterizedTest
    @EnumSource(value = AS2MessageStructure.class,
                names = { "SIGNED", "COMPRESSED_SIGNED", "SIGNED_COMPRESSED" })
    public void unverifiableSignedMessageRejectedTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_INSUFFICIENT_MESSAGE_SECURITY);
    }

    // An unsigned message has no signature to verify, so signatureVerificationRequired has no effect on it.
    @ParameterizedTest
    @EnumSource(value = AS2MessageStructure.class,
                names = { "PLAIN", "PLAIN_COMPRESSED" })
    public void unsignedMessageStillDeliveredTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnSuccessDisposition(context);
    }
}
