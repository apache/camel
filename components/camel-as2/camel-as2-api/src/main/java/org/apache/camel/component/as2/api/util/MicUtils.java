/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.camel.component.as2.api.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MicAlgorithm;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.entity.EntityUtils;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicUtils {
    private static final Logger LOG = LoggerFactory.getLogger(MicUtils.class);
    
    public static class ReceivedContentMic {
        private final String digestAlgorithmId;
        private final String encodedMessageDigest;
        
        public ReceivedContentMic(String digestAlgorithmId, byte[] messageDigest) throws Exception {
            this.digestAlgorithmId = digestAlgorithmId;
            messageDigest = EntityUtils.encode(messageDigest, "base64");
            this.encodedMessageDigest = new String(messageDigest, AS2CharSet.US_ASCII);
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
            LOG.debug("failed to get message digets '" + algorithmId + "'");
            return null;
        }
    }
    
    public static ReceivedContentMic createReceivedContentMic(HttpEntityEnclosingRequest request) throws HttpException {
        
        String dispositionNotificationOptionsString =  HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS);
        if (dispositionNotificationOptionsString == null) {
            LOG.debug("can not create MIC: disposition notification options missing from request");
            return null;
        }
        DispositionNotificationOptions dispositionNotificationOptions = DispositionNotificationOptionsParser.parseDispositionNotificationOptions(dispositionNotificationOptionsString, null);
        String micAlgorithm = getMicJdkAlgorithmName(dispositionNotificationOptions.getSignedReceiptMicalg().getValues());
        if (micAlgorithm == null) {
            LOG.debug("can not create MIC: no matching MIC algorithms found");
            return null;
        }

        String contentTypeString = HttpMessageUtils.getHeaderValue(request, AS2Header.CONTENT_TYPE);
        if(contentTypeString == null) {
            LOG.debug("can not create MIC: content type missing from request");
            return null;
        }
        ContentType contentType = ContentType.parse(contentTypeString);
        
        HttpEntity entity = null;
        switch(contentType.getMimeType().toLowerCase()) {
        case AS2MimeType.APPLICATION_EDIFACT:
        case AS2MimeType.APPLICATION_EDI_X12:
        case AS2MimeType.APPLICATION_EDI_CONSENT: {
            EntityParser.parseAS2MessageEntity(request);
            entity = HttpMessageUtils.getEntity(request, ApplicationEDIEntity.class);
            break;
        }
        case AS2MimeType.MULTIPART_SIGNED: {
            EntityParser.parseAS2MessageEntity(request);
            MultipartSignedEntity multipartSignedEntity = HttpMessageUtils.getEntity(request, MultipartSignedEntity.class);
            entity = multipartSignedEntity.getSignedDataEntity();
            break;
        }
         default:
             LOG.debug("can not create MIC: invalid content type '" + contentType.getMimeType() + "' for message integrity check");
             return null;
        }
        
        byte[] content = EntityUtils.getContent(entity);
        
        byte[] mic = createMic(content, micAlgorithm);
        try {
            return new ReceivedContentMic(micAlgorithm, mic);
        } catch (Exception e) {
            throw new HttpException("failed to encode MIC", e);
        }
    }
    
    private static String getMicJdkAlgorithmName(String[] micAs2AlgorithmNames) {
        if (micAs2AlgorithmNames == null) {
            return null;
        }
        for(String micAs2AlgorithmName : micAs2AlgorithmNames) {
            String micAlgorithmName = AS2MicAlgorithm.getJdkAlgorithmName(micAs2AlgorithmName);
            if (micAlgorithmName != null) {
                return micAlgorithmName;
            }
        }    
        return null;
    }
    
}
