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

import java.io.IOException;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.api.util.MicUtils;
import org.apache.camel.component.as2.api.util.MicUtils.ReceivedContentMic;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.Args;

public class AS2MessageDispositionNotificationEntity extends MimeEntity {

    private static final String ADDRESS_TYPE_PREFIX = "rfc822;";
    private static final String MTA_NAME_TYPE_PREFIX = "dns;";
    private static final String REPORTING_UA = "Reporting-UA";
    private static final String MDN_GATEWAY = "MDN-Gateway";
    private static final String FINAL_RECIPIENT = "Final-Recipient";
    private static final String ORIGINAL_MESSAGE_ID = "Original-Message-ID";
    private static final String AS2_DISPOSITION = "Disposition";
    private static final String FAILURE = "Failure";
    private static final String ERROR = "Error";
    private static final String WARNING = "Warning";
    private static final String RECEIVED_CONTENT_MIC = "Received-content-MIC";

    private String reportingUA;
    // TODO determine if we need to support this field.
    private String mtnName;
    private String finalRecipient;
    private String originalMessageId;
    private DispositionMode dispositionMode;
    private AS2DispositionType dispositionType;
    private AS2DispositionModifier dispositionModifier;
    private String[] failureFields;
    private String[] errorFields;
    private String[] warningFields;
    private Map<String, String> extensionFields = new HashMap<>();
    private ReceivedContentMic receivedContentMic;

    public AS2MessageDispositionNotificationEntity(HttpEntityEnclosingRequest request,
                                                   HttpResponse response,
                                                   DispositionMode dispositionMode,
                                                   AS2DispositionType dispositionType,
                                                   AS2DispositionModifier dispositionModifier,
                                                   String[] failureFields,
                                                   String[] errorFields,
                                                   String[] warningFields,
                                                   Map<String, String> extensionFields,
                                                   String charset,
                                                   boolean isMainBody,
                                                   PrivateKey decryptingPrivateKey) throws HttpException {
        setMainBody(isMainBody);
        setContentType(ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, charset));

        this.finalRecipient = HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_TO);
        if (this.finalRecipient == null) {
            throw new HttpException("The " + AS2Header.AS2_TO + " is missing");
        }

        this.originalMessageId  = HttpMessageUtils.getHeaderValue(request, AS2Header.MESSAGE_ID);

        this.receivedContentMic = MicUtils.createReceivedContentMic(request, decryptingPrivateKey);

        this.reportingUA = HttpMessageUtils.getHeaderValue(response, AS2Header.SERVER);

        this.dispositionMode = Args.notNull(dispositionMode, "Disposition Mode");
        this.dispositionType = Args.notNull(dispositionType, "Disposition Type");
        this.dispositionModifier = dispositionModifier;
        this.failureFields = failureFields;
        this.errorFields = errorFields;
        this.warningFields = warningFields;
        if (extensionFields == null || extensionFields.isEmpty()) {
            this.extensionFields.clear();
        } else {
            this.extensionFields.putAll(extensionFields);
        }
    }

    public AS2MessageDispositionNotificationEntity(String reportingUA,
                                                   String mtnName,
                                                   String finalRecipient,
                                                   String originalMessageId,
                                                   DispositionMode dispositionMode,
                                                   AS2DispositionType dispositionType,
                                                   AS2DispositionModifier dispositionModifier,
                                                   String[] failureFields,
                                                   String[] errorFields,
                                                   String[] warningFields,
                                                   Map<String, String> extensionFields,
                                                   ReceivedContentMic receivedContentMic) {
        this.reportingUA = reportingUA;
        this.mtnName = mtnName;
        this.finalRecipient = finalRecipient;
        this.originalMessageId = originalMessageId;
        this.dispositionMode = dispositionMode;
        this.dispositionType = dispositionType;
        this.dispositionModifier = dispositionModifier;
        this.failureFields = failureFields;
        this.errorFields = errorFields;
        this.warningFields = warningFields;
        this.extensionFields = extensionFields;
        this.receivedContentMic = receivedContentMic;
    }

    public String getReportingUA() {
        return reportingUA;
    }

    public String getMtnName() {
        return mtnName;
    }

    public String getFinalRecipient() {
        return finalRecipient;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public DispositionMode getDispositionMode() {
        return dispositionMode;
    }

    public AS2DispositionType getDispositionType() {
        return dispositionType;
    }

    public AS2DispositionModifier getDispositionModifier() {
        return dispositionModifier;
    }

    public String[] getFailureFields() {
        return failureFields;
    }

    public String[] getErrorFields() {
        return errorFields;
    }

    public String[] getWarningFields() {
        return warningFields;
    }

    public Map<String, String> getExtensionFields() {
        return extensionFields;
    }

    public ReceivedContentMic getReceivedContentMic() {
        return receivedContentMic;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);
        try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, AS2Charset.US_ASCII)) {

            // Write out mime part headers if this is not the main body of
            // message.
            if (!isMainBody()) {
                HeaderIterator it = headerIterator();
                while (it.hasNext()) {
                    Header header = it.nextHeader();
                    canonicalOutstream.writeln(header.toString());
                }
                canonicalOutstream.writeln(); // ensure empty line between
                                              // headers and body; RFC2046 -
                                              // 5.1.1
            }

            if (reportingUA != null) {
                Header reportingUAField = new BasicHeader(REPORTING_UA, reportingUA);
                canonicalOutstream.writeln(reportingUAField.toString());
            }

            if (mtnName != null) {
                Header mdnGatewayField = new BasicHeader(MDN_GATEWAY, MTA_NAME_TYPE_PREFIX + mtnName);
                canonicalOutstream.writeln(mdnGatewayField.toString());
            }

            Header finalRecipientField = new BasicHeader(FINAL_RECIPIENT, ADDRESS_TYPE_PREFIX + finalRecipient);
            canonicalOutstream.writeln(finalRecipientField.toString());

            if (originalMessageId != null) {
                Header originalMessageIdField = new BasicHeader(ORIGINAL_MESSAGE_ID, originalMessageId);
                canonicalOutstream.writeln(originalMessageIdField.toString());
            }

            String as2Disposition = dispositionMode.toString() + ";" + dispositionType.toString();
            if (dispositionModifier != null) {
                as2Disposition = as2Disposition + "/" + dispositionModifier.toString();
            }
            Header as2DispositionField = new BasicHeader(AS2_DISPOSITION, as2Disposition);
            canonicalOutstream.writeln(as2DispositionField.toString());

            if (failureFields != null) {
                for (String field : failureFields) {
                    Header failureField = new BasicHeader(FAILURE, field);
                    canonicalOutstream.writeln(failureField.toString());
                }
            }

            if (errorFields != null) {
                for (String field : errorFields) {
                    Header errorField = new BasicHeader(ERROR, field);
                    canonicalOutstream.writeln(errorField.toString());
                }
            }

            if (failureFields != null) {
                for (String field : failureFields) {
                    Header failureField = new BasicHeader(WARNING, field);
                    canonicalOutstream.writeln(failureField.toString());
                }
            }

            if (extensionFields != null) {
                for (Entry<String, String> entry : extensionFields.entrySet()) {
                    Header failureField = new BasicHeader(entry.getKey(), entry.getValue());
                    canonicalOutstream.writeln(failureField.toString());
                }
            }

            if (receivedContentMic != null) {
                Header as2ReceivedContentMicField = new BasicHeader(RECEIVED_CONTENT_MIC,
                        receivedContentMic.toString());
                canonicalOutstream.writeln(as2ReceivedContentMicField.toString());
            }
        }
    }

}
