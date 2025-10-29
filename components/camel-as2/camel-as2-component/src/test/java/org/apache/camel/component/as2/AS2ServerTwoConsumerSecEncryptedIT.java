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

import java.security.PrivateKey;

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
 * Tests an AS2 server configured with a decryption key to decrypt AS2 Messages. <br>
 * Only messages with sufficient encryption will be processed, for instance, 'signed-encrypted',
 * 'compressed-signed-encrypted', and 'signed-compressed-encrypted'. <br>
 * All other message structures will return an 'insufficient-message-security' error due to insufficient encryption,
 * e.g. 'plain', 'plain-compressed' etc. <br>
 * Any decryption failure will return an 'decryption-failed' error.
 */
public class AS2ServerTwoConsumerSecEncryptedIT extends AS2ServerTwoConsumerBase {

    // verify message types that fail with insufficient security due to lack of encryption
    @ParameterizedTest
    @EnumSource(value = AS2MessageStructure.class,
                names = { "PLAIN", "SIGNED", "PLAIN_COMPRESSED", "COMPRESSED_SIGNED", "SIGNED_COMPRESSED" })
    public void insufficientEncryptionFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendToConsumerB(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_INSUFFICIENT_MESSAGE_SECURITY);
    }

    // verify message types that are successfully decrypted
    @ParameterizedTest
    @EnumSource(value = AS2MessageStructure.class,
                names = {
                        "ENCRYPTED", "SIGNED_ENCRYPTED", "ENCRYPTED_COMPRESSED", "ENCRYPTED_COMPRESSED_SIGNED",
                        "ENCRYPTED_SIGNED_COMPRESSED" })
    public void successfullyProcessedTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendToConsumerB(messageStructure);
        verifyOkResponse(context);
        verifyMdnSuccessDisposition(context);
    }

    // utility method to reproduce the MIC and compare against the MIC received in MDN.
    @Override
    protected MicUtils.ReceivedContentMic createReceivedContentMic(HttpRequest request) throws HttpException {
        final String requestUri = request.getPath();
        final PrivateKey currentSigningKey = getSigningPrivateKeyByRequestUri(requestUri);
        return MicUtils.createReceivedContentMic((ClassicHttpRequest) request, null, currentSigningKey);
    }
}
