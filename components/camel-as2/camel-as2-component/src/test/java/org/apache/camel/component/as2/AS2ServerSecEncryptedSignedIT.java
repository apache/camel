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

import java.security.cert.Certificate;

import org.apache.camel.CamelContext;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests an AS2 server configured with a decryption key to decrypt AS2 Messages and a signing certificate to verify AS2
 * Message signatures. <br>
 * Only messages with sufficient encryption and a valid signature will be processed, for instance, 'signed-encrypted',
 * 'compressed-signed-encrypted', and 'signed-compressed-encrypted'. <br>
 * All other message structures will return an 'insufficient-message-security' error due to either insufficient
 * encryption, e.g. 'plain', 'plain-compressed' etc. or lack of signature, e.g. 'encrypted', 'encrypted-compressed' etc.
 * <br>
 * Any decryption failure will return an 'decryption-failed' error. <br>
 * Failure to verify a provided signature will return an 'authentication-failed' error.
 */
public class AS2ServerSecEncryptedSignedIT extends AS2ServerSecTestBase {

    // verify message types that fail with insufficient security due to lack of encryption
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"PLAIN", "SIGNED", "PLAIN_COMPRESSED", "COMPRESSED_SIGNED", "SIGNED_COMPRESSED"})
    public void insufficientEncryptionFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_INSUFFICIENT_MESSAGE_SECURITY);
    }

    // verify message types that fail with insufficient security due to lack of signature
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"ENCRYPTED", "ENCRYPTED_COMPRESSED"})
    public void noSignatureFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_INSUFFICIENT_MESSAGE_SECURITY);
    }

    // verify the message types that pass decryption and signature verification
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"SIGNED_ENCRYPTED", "ENCRYPTED_COMPRESSED_SIGNED", "ENCRYPTED_SIGNED_COMPRESSED"})
    public void successfullyProcessedTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnSuccessDisposition(context);
    }

    // verify that message types with invalid signature fail authentication
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"SIGNED_ENCRYPTED", "ENCRYPTED_COMPRESSED_SIGNED", "ENCRYPTED_SIGNED_COMPRESSED"})
    public void invalidSigningCertFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendWithInvalidSignature(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_AUTHENTICATION_FAILED);
    }

    // verify that message types that fail decryption when encrypted with invalid cert
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"SIGNED_ENCRYPTED", "ENCRYPTED_COMPRESSED_SIGNED", "ENCRYPTED_SIGNED_COMPRESSED"})
    public void invalidEncryptionFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendWithInvalidEncryption(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_DECRYPTION_FAILED);
    }

    // utility method to reproduce the MIC and compare against the MIC received in MDN.
    @Override
    protected MicUtils.ReceivedContentMic createReceivedContentMic(HttpRequest request) throws HttpException {
        return MicUtils.createReceivedContentMic(
                (ClassicHttpRequest) request, new Certificate[] {signingCert}, signingKP.getPrivate());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        AS2Component as2Component = (AS2Component) context.getComponent("as2");
        AS2Configuration configuration = as2Component.getConfiguration();
        // decryption key
        configuration.setDecryptingPrivateKey(decryptingKP.getPrivate());
        // signature validation cert
        configuration.setValidateSigningCertificateChain(new Certificate[] {signingCert});
        return context;
    }
}
