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

import java.security.PrivateKey;
import java.util.Map;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;

public class DispositionNotificationMultipartReportEntity extends MultipartReportEntity {
    
    protected DispositionNotificationMultipartReportEntity(String boundary, boolean isMainBody) {
        this.boundary = boundary;
        this.isMainBody = isMainBody;
        removeHeaders(AS2Header.CONTENT_TYPE);
        setContentType(getContentTypeValue(boundary));
    }

    public DispositionNotificationMultipartReportEntity(HttpEntityEnclosingRequest request,
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
                                                        String mdnMessage)
            throws HttpException {
        super(charset, isMainBody, boundary);
        removeHeaders(AS2Header.CONTENT_TYPE);
        setContentType(getContentTypeValue(boundary));
        Args.notNull(dispositionMode, "dispositionMode");
        Args.notNull(dispositionType, "dispositionType");
        Args.notNull(mdnMessage, "mdnMessageTemplate"); 

        addPart(buildPlainTextReport(mdnMessage));
        addPart(new AS2MessageDispositionNotificationEntity(request, response, dispositionMode, dispositionType,
                dispositionModifier, failureFields, errorFields, warningFields, extensionFields, charset, false, decryptingPrivateKey));
    }

    public String getMainMessageContentType() {
        return AS2MimeType.MULTIPART_REPORT + "; report-type=disposition-notification; boundary=\"" + boundary + "\"";
    }

    protected TextPlainEntity buildPlainTextReport(String mdnMessage)
            throws HttpException {
        return new TextPlainEntity(mdnMessage, AS2Charset.US_ASCII, AS2TransferEncoding.SEVENBIT, false);
    }

    protected String getContentTypeValue(String boundary) {
        ContentType contentType = ContentType.parse(AS2MimeType.MULTIPART_REPORT + ";"
                + "report-type=disposition-notification; boundary=\"" + boundary + "\"");
        return contentType.toString();
    }
    
}
