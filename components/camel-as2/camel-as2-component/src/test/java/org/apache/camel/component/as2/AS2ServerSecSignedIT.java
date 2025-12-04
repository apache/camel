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
 * Tests an AS2 server configured with a signing certificate to verify AS2 Message signatures. <br>
 * Only messages with a valid signature will be processed, for instance, 'signed', 'compressed-signed', and
 * 'signed-compressed'. <br>
 * Any encrypted message received will return a 'decryption-failed' exception due to the lack of configured decryption
 * key. <br>
 * Any unsigned message will return an 'insufficient-message-security' error. <br>
 * Failure to verify a signature will return an 'authentication-failed' error.
 */
public class AS2ServerSecSignedIT extends AS2ServerSecTestBase {

    // verify message types that fail with insufficient security due to lack of signature
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"PLAIN", "PLAIN_COMPRESSED"})
    public void noSignatureFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_INSUFFICIENT_MESSAGE_SECURITY);
    }

    // verify that message types that fail decryption when the server does not have a decrypting key
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {
                "ENCRYPTED",
                "SIGNED_ENCRYPTED",
                "ENCRYPTED_COMPRESSED",
                "ENCRYPTED_COMPRESSED_SIGNED",
                "ENCRYPTED_SIGNED_COMPRESSED"
            })
    public void invalidEncryptionFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendWithInvalidEncryption(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_DECRYPTION_FAILED);
    }

    // verify the message types that pass signature verification
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"SIGNED", "COMPRESSED_SIGNED", "SIGNED_COMPRESSED"})
    public void successfullyProcessedTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnSuccessDisposition(context);
    }

    // verify that message types with invalid signature fail authentication
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"SIGNED", "COMPRESSED_SIGNED", "SIGNED_COMPRESSED"})
    public void invalidSigningCertFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendWithInvalidSignature(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_AUTHENTICATION_FAILED);
    }

    // utility method to reproduce the MIC and compare against the MIC received in MDN.
    @Override
    protected MicUtils.ReceivedContentMic createReceivedContentMic(HttpRequest request) throws HttpException {
        return MicUtils.createReceivedContentMic((ClassicHttpRequest) request, new Certificate[] {signingCert}, null);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        AS2Component as2Component = (AS2Component) context.getComponent("as2");
        AS2Configuration configuration = as2Component.getConfiguration();
        // signature validation cert
        configuration.setValidateSigningCertificateChain(new Certificate[] {signingCert});
        return context;
    }
}
