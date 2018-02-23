/**
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
package org.apache.camel.component.as2.api.entity;

import java.util.Map;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.apache.http.util.CharArrayBuffer;

public class MultipartReportEntity extends MultipartMimeEntity {

    public MultipartReportEntity(String reportingUA,
                                 String mtnName,
                                 String originalRecipient,
                                 String finalRecipient,
                                 String originalMessageId,
                                 DispositionMode dispositionMode,
                                 AS2DispositionType dispositionType,
                                 AS2DispositionModifier dispositionModifier,
                                 String[] failureFields,
                                 String[] errorFields,
                                 String[] warningFields,
                                 Map<String, String> extensionFields,
                                 String encodedMessageDigest,
                                 String digestAlgorithmId,
                                 String charset,
                                 boolean isMainBody,
                                 String boundary) {

        super(ContentType.create(AS2MimeType.MULTIPART_REPORT, charset), isMainBody, boundary);
        addPart(buildPlainTextReport(reportingUA, mtnName, originalRecipient, finalRecipient, originalMessageId,
                dispositionMode, dispositionType, dispositionModifier, failureFields, errorFields, warningFields, extensionFields, encodedMessageDigest, digestAlgorithmId));
        addPart(new AS2MessageDispositionNotificationEntity(reportingUA, mtnName, originalRecipient, finalRecipient,
                originalMessageId, dispositionMode, dispositionType, dispositionModifier, failureFields, errorFields, warningFields, extensionFields, encodedMessageDigest, digestAlgorithmId, charset,
                isMainBody));
    }

    public MultipartReportEntity(HttpEntityEnclosingRequest request,
                                 String reportingUA,
                                 String mtnName,
                                 DispositionMode dispositionMode,
                                 AS2DispositionType dispositionType,
                                 AS2DispositionModifier dispositionModifier,
                                 String[] failureFields,
                                 String[] errorFields,
                                 String[] warningFields,
                                 Map<String, String> extensionFields,
                                 String encodedMessageDigest,
                                 String digestAlgorithmId,
                                String charset,
                                 boolean isMainBody,
                                 String boundary) throws HttpException {

        super(ContentType.create(AS2MimeType.MULTIPART_REPORT, charset), isMainBody, boundary);
        
        String originalRecipient, finalRecipient;
        originalRecipient = finalRecipient = HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_TO);
        
        String originalMessageId  = HttpMessageUtils.getHeaderValue(request, AS2Header.MESSAGE_ID);
        if (originalMessageId == null) {
            throw new HttpException("The " + AS2Header.MESSAGE_ID + " is missing");
        }


        addPart(buildPlainTextReport(reportingUA, mtnName, originalRecipient, finalRecipient, originalMessageId,
                dispositionMode, dispositionType, dispositionModifier, failureFields, errorFields, warningFields, extensionFields, encodedMessageDigest, digestAlgorithmId));
        addPart(new AS2MessageDispositionNotificationEntity(reportingUA, mtnName, originalRecipient, finalRecipient,
                originalMessageId, dispositionMode, dispositionType, dispositionModifier, failureFields, errorFields, warningFields, extensionFields, encodedMessageDigest, digestAlgorithmId, charset,
                isMainBody));
    }

    protected TextPlainEntity buildPlainTextReport(String reportingUA,
                                                   String mtnName,
                                                   String originalRecipient,
                                                   String finalRecipient,
                                                   String originalMessageId,
                                                   DispositionMode dispositionMode,
                                                   AS2DispositionType dispositionType,
                                                   AS2DispositionModifier dispositionModifier,
                                                   String[] failureFields,
                                                   String[] errorFields,
                                                   String[] warningFields,
                                                   Map<String, String> extensionFields,
                                                   String encodedMessageDigest,
                                                   String digestAlgorithmId) {

        CharArrayBuffer charBuffer = new CharArrayBuffer(10);

        charBuffer.append("MDN for -\n");
        charBuffer.append(" Message ID: " + originalMessageId + "\n");
        charBuffer.append("  From: " + "\n");
        charBuffer.append("  To: " + "\n");
        charBuffer.append("  Received on: " + "\n");
        charBuffer.append(" Status: " + "\n");
        charBuffer.append(" Comment: " + "\n");

        return new TextPlainEntity(charBuffer.toString(), AS2CharSet.US_ASCII, AS2TransferEncoding._7BIT, false);
    }

}
