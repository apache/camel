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
package org.apache.camel.component.as2.api.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MicAlgorithm;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MicUtils {
    private static final Logger LOG = LoggerFactory.getLogger(MicUtils.class);

    private MicUtils() {
    }

    public static class ReceivedContentMic {
        private final String digestAlgorithmId;
        private final String encodedMessageDigest;

        public ReceivedContentMic(String digestAlgorithmId, byte[] messageDigest) throws Exception {
            this.digestAlgorithmId = digestAlgorithmId;
            messageDigest = EntityUtils.encode(messageDigest, "base64");
            this.encodedMessageDigest = new String(messageDigest, AS2Charset.US_ASCII);
        }

        // Used when parsing received content MIC from received string
        protected ReceivedContentMic(String digestAlgorithmId, String encodedMessageDigest) {
            this.digestAlgorithmId = digestAlgorithmId;
            this.encodedMessageDigest = encodedMessageDigest;
        }

        public String getDigestAlgorithmId() {
            return digestAlgorithmId;
        }

        public String getEncodedMessageDigest() {
            return encodedMessageDigest;
        }

        @Override
        public String toString() {
            return encodedMessageDigest + "," + digestAlgorithmId;
        }
    }

    public static byte[] createMic(byte[] content, String algorithmId) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithmId, "BC");
            return messageDigest.digest(content);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            LOG.debug("failed to get message digets '{}'", algorithmId);
            return null;
        }
    }

    public static ReceivedContentMic createReceivedContentMic(HttpEntityEnclosingRequest request, PrivateKey decryptingPrivateKey) throws HttpException {

        String dispositionNotificationOptionsString =  HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS);
        if (dispositionNotificationOptionsString == null) {
            LOG.debug("do not create MIC: no disposition notification options in request");
            return null;
        }
        DispositionNotificationOptions dispositionNotificationOptions = DispositionNotificationOptionsParser.parseDispositionNotificationOptions(dispositionNotificationOptionsString, null);
        String micJdkAlgorithmName = getMicJdkAlgorithmName(dispositionNotificationOptions.getSignedReceiptMicalg().getValues());
        if (micJdkAlgorithmName == null) {
            LOG.debug("do not create MIC: no matching MIC algorithms found");
            return null;
        }

        HttpEntity entity = HttpMessageUtils.extractEdiPayload(request, decryptingPrivateKey);

        byte[] content = EntityUtils.getContent(entity);

        String micAS2AlgorithmName = AS2MicAlgorithm.getAS2AlgorithmName(micJdkAlgorithmName);
        byte[] mic = createMic(content, micJdkAlgorithmName);
        try {
            return new ReceivedContentMic(micAS2AlgorithmName, mic);
        } catch (Exception e) {
            throw new HttpException("Failed to encode MIC", e);
        }
    }

    public static String getMicJdkAlgorithmName(String[] micAs2AlgorithmNames) {
        if (micAs2AlgorithmNames == null) {
            return AS2MicAlgorithm.SHA_1.getJdkAlgorithmName();
        }
        for (String micAs2AlgorithmName : micAs2AlgorithmNames) {
            String micJdkAlgorithmName = AS2MicAlgorithm.getJdkAlgorithmName(micAs2AlgorithmName);
            if (micJdkAlgorithmName != null) {
                return micJdkAlgorithmName;
            }
        }
        return AS2MicAlgorithm.SHA_1.getJdkAlgorithmName();
    }
}
