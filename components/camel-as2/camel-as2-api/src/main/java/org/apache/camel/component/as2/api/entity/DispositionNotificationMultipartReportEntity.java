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

package org.apache.camel.component.as2.api.entity;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Map;

import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;

public class DispositionNotificationMultipartReportEntity extends MultipartReportEntity {

    protected DispositionNotificationMultipartReportEntity(
            String boundary, String contentTransferEncoding, boolean isMainBody) {
        super(
                ContentType.parse(AS2MimeType.MULTIPART_REPORT + ";"
                        + "report-type=disposition-notification; boundary=\"" + boundary + "\""),
                contentTransferEncoding,
                isMainBody,
                boundary);
    }

    public DispositionNotificationMultipartReportEntity(
            ClassicHttpRequest request,
            HttpResponse response,
            DispositionMode dispositionMode,
            AS2DispositionType dispositionType,
            AS2DispositionModifier dispositionModifier,
            String[] failureFields,
            String[] errorFields,
            String[] warningFields,
            Map<String, String> extensionFields,
            String charset,
            String boundary,
            boolean isMainBody,
            PrivateKey decryptingPrivateKey,
            String mdnMessage,
            Certificate[] validateSigningCertificateChain)
            throws HttpException {
        super(
                ContentType.parse(AS2MimeType.MULTIPART_REPORT + ";"
                        + "report-type=disposition-notification; boundary=\"" + boundary + "\""),
                null,
                isMainBody,
                boundary);
        ObjectHelper.notNull(dispositionMode, "dispositionMode");
        ObjectHelper.notNull(dispositionType, "dispositionType");
        ObjectHelper.notNull(mdnMessage, "mdnMessageTemplate");

        addPart(buildPlainTextReport(mdnMessage));
        addPart(new AS2MessageDispositionNotificationEntity(
                request,
                response,
                dispositionMode,
                dispositionType,
                dispositionModifier,
                failureFields,
                errorFields,
                warningFields,
                extensionFields,
                charset,
                false,
                decryptingPrivateKey,
                validateSigningCertificateChain));
    }

    public String getMainMessageContentType() {
        return AS2MimeType.MULTIPART_REPORT + "; report-type=disposition-notification; boundary=\"" + boundary + "\"";
    }

    protected TextPlainEntity buildPlainTextReport(String mdnMessage) {
        return new TextPlainEntity(mdnMessage, StandardCharsets.US_ASCII.name(), AS2TransferEncoding.SEVENBIT, false);
    }
}
