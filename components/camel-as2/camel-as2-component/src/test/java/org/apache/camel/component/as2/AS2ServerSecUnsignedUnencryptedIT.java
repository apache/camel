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
 * Tests an AS2 server for when it is configured with neither a key to decrypt AS2 Messages nor a signing certificate to
 * verify signatures. <br>
 * Any encrypted message received will return a 'decryption-failed' exception due to the lack of configured decryption
 * key. <br>
 * Any signature is ignored.
 */
public class AS2ServerSecUnsignedUnencryptedIT extends AS2ServerSecTestBase {

    // verify message types that fail decryption when encrypted with an invalid cert
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
    public void cannotDecryptFailureTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = sendWithInvalidEncryption(messageStructure);
        verifyOkResponse(context);
        verifyMdnErrorDisposition(context, AS2DispositionModifier.ERROR_DECRYPTION_FAILED);
    }

    // verify message types that are successfully processed
    @ParameterizedTest
    @EnumSource(
            value = AS2MessageStructure.class,
            names = {"PLAIN", "SIGNED", "PLAIN_COMPRESSED", "COMPRESSED_SIGNED", "SIGNED_COMPRESSED"})
    public void successfullyProcessedTest(AS2MessageStructure messageStructure) throws Exception {
        HttpCoreContext context = send(messageStructure);
        verifyOkResponse(context);
        verifyMdnSuccessDisposition(context);
    }
}
