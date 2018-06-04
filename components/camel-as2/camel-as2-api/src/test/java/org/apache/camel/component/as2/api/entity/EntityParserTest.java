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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.message.BasicHttpResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EntityParserTest {
    
    public static final String REPORT_CONTENT_TYPE_VALUE =
            "multipart/report; report-type=disposition-notification; boundary=\"----=_Part_56_1672293592.1028122454656\"";

    public static final String REPORT_TYPE_HEADER_VALUE =
            "disposition-notification; boundary=\"----=_Part_56_1672293592.1028122454656\"\r\n";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT =
            "\r\n"
            + "------=_Part_56_1672293592.1028122454656\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n" + "\r\n"
            + "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n" + "\r\n"
            + "------=_Part_56_1672293592.1028122454656\r\n"
            + "Content-Type: message/disposition-notification\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n" + "\r\n"
            + "Reporting-UA: AS2 Server\r\n"
            + "MDN-Gateway: dns; example.com\r\n"
            + "Original-Recipient: rfc822; 0123456780000\r\n"
            + "Final-Recipient: rfc822; 0123456780000\r\n"
            + "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "Disposition: automatic-action/MDN-sent-automatically;\r\n"
            + "  processed/warning: you're awesome\r\n"
            + "Failure: oops-a-failure\r\n" + "Error: oops-an-error\r\n"
            + "Warning: oops-a-warning\r\n"
            + "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n"
            + "\r\n"
            + "------=_Part_56_1672293592.1028122454656--\r\n";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String TEXT_PLAIN_CONTENT =
            "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n"
            + "\r\n"
            + "------=_Part_56_1672293592.1028122454656--\r\n";

    public static final String TEXT_PLAIN_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";

    public static final String TEXT_PLAIN_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String TEXT_PLAIN_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_TEXT_PLAIN_CONTENT =
            "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n";

    public static final String DISPOSITION_NOTIFICATION_CONTENT =
            "Reporting-UA: AS2 Server\r\n"
            + "MDN-Gateway: dns; example.com\r\n"
            + "Original-Recipient: rfc822; 0123456780000\r\n"
            + "Final-Recipient: rfc822; 0123456780000\r\n"
            + "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "Disposition: automatic-action/MDN-sent-automatically;\r\n"
            + "  processed/warning: you're awesome\r\n"
            + "Failure: oops-a-failure\r\n" + "Error: oops-an-error\r\n"
            + "Warning: oops-a-warning\r\n"
            + "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n"
            + "\r\n"
            + "------=_Part_56_1672293592.1028122454656--\r\n";

    public static final String DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";

    public static final String DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_REPORTING_UA = "AS2 Server";
    public static final String EXPECTED_MTN_NAME = "example.com";
    public static final String EXPECTED_ORIGINAL_RECIPIENT = "rfc822; 0123456780000";
    public static final String EXPECTED_FINAL_RECIPIENT = "0123456780000";
    public static final String EXPECTED_ORIGINAL_MESSAGE_ID = "<200207310834482A70BF63@\\\"~~foo~~\\\">";
    public static final DispositionMode EXPECTED_DISPOSITION_MODE = DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY;
    public static final String EXPECTED_DISPOSITION_MODIFIER = "warning: you're awesome";
    public static final AS2DispositionType EXPECTED_DISPOSITION_TYPE = AS2DispositionType.PROCESSED;
    public static final String[] EXPECTED_FAILURE = {"oops-a-failure"};
    public static final String[] EXPECTED_ERROR = {"oops-an-error"};
    public static final String[] EXPECTED_WARNING = {"oops-a-warning"};
    public static final String EXPECTED_ENCODED_MESSAGE_DIGEST = "7v7F++fQaNB1sVLFtMRp+dF+eG4=";
    public static final String EXPECTED_DIGEST_ALGORITHM_ID = "sha1";

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void parseMessageDispositionNotificationReportMessageTest() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, EnglishReasonPhraseCatalog.INSTANCE.getReason(HttpStatus.SC_OK, null));
        HttpMessageUtils.setHeaderValue(response, AS2Header.CONTENT_TRANSFER_ENCODING, DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING);

        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentType(REPORT_CONTENT_TYPE_VALUE);
        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_REPORT_CONTENT.getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        entity.setContent(is);
        EntityUtils.setMessageEntity(response, entity);

        EntityParser.parseMessageDispositionNotificationReportEntity(response);
        HttpEntity parsedEntity = EntityUtils.getMessageEntity(response);
        assertNotNull("Unexpected Null message disposition notification report entity", parsedEntity);
        assertTrue("Unexpected type for message disposition notification report entity", parsedEntity instanceof DispositionNotificationMultipartReportEntity);
    }

    @Test
    public void parseMessageDispositionNotificationReportBodyTest() throws Exception {

        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_REPORT_CONTENT.getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = EntityParser
                .parseMultipartReportEntityBody(inbuffer, DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY,
                        DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME,
                        DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING);

        assertNotNull("Unexpected Null disposition notification multipart entity", dispositionNotificationMultipartReportEntity);
        assertEquals("Unexpected number of body parts", 2, dispositionNotificationMultipartReportEntity.getPartCount());

        assertTrue("Unexpected type for first body part", dispositionNotificationMultipartReportEntity.getPart(0) instanceof TextPlainEntity);
        assertTrue("Unexpected type for second body part", dispositionNotificationMultipartReportEntity.getPart(1) instanceof AS2MessageDispositionNotificationEntity);
    }

    @Test
    public void parseTextPlainBodyTest() throws Exception {

        InputStream is = new ByteArrayInputStream(TEXT_PLAIN_CONTENT.getBytes(TEXT_PLAIN_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        TextPlainEntity textPlainEntity = EntityParser.parseTextPlainEntityBody(inbuffer, TEXT_PLAIN_CONTENT_BOUNDARY, TEXT_PLAIN_CONTENT_CHARSET_NAME, TEXT_PLAIN_CONTENT_TRANSFER_ENCODING);

        String text = textPlainEntity.getText();

        assertEquals("Unexpected text", EXPECTED_TEXT_PLAIN_CONTENT, text);
    }

    @Test
    public void parseMessageDispositionNotificationBodyTest() throws Exception {

        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_CONTENT.getBytes(DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity = EntityParser
                .parseMessageDispositionNotificationEntityBody(inbuffer, DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY,
                        DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME,
                        DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING);

        assertEquals("Unexpected Reporting UA value", EXPECTED_REPORTING_UA, messageDispositionNotificationEntity.getReportingUA());
        assertEquals("Unexpected MTN Name", EXPECTED_MTN_NAME, messageDispositionNotificationEntity.getMtnName());
        assertEquals("Unexpected Original Recipient", EXPECTED_ORIGINAL_RECIPIENT, messageDispositionNotificationEntity.getExtensionFields().get("Original-Recipient"));
        assertEquals("Unexpected Final Reciptient", EXPECTED_FINAL_RECIPIENT, messageDispositionNotificationEntity.getFinalRecipient());
        assertEquals("Unexpected Original Message ID", EXPECTED_ORIGINAL_MESSAGE_ID, messageDispositionNotificationEntity.getOriginalMessageId());
        assertEquals("Unexpected Disposition Mode", EXPECTED_DISPOSITION_MODE, messageDispositionNotificationEntity.getDispositionMode());
        assertNotNull("Unexpected Null Disposition Modifier", messageDispositionNotificationEntity.getDispositionModifier());
        assertEquals("Unexpected Disposition Modifier", EXPECTED_DISPOSITION_MODIFIER, messageDispositionNotificationEntity.getDispositionModifier().getModifier());
        assertEquals("Unexpected Disposition Type", EXPECTED_DISPOSITION_TYPE, messageDispositionNotificationEntity.getDispositionType());
        assertArrayEquals("Unexpected Failure Array value", EXPECTED_FAILURE, messageDispositionNotificationEntity.getFailureFields());
        assertArrayEquals("Unexpected Error Array value", EXPECTED_ERROR, messageDispositionNotificationEntity.getErrorFields());
        assertArrayEquals("Unexpected Warning Array value", EXPECTED_WARNING, messageDispositionNotificationEntity.getWarningFields());
        assertNotNull("Unexpected Null Received Content MIC", messageDispositionNotificationEntity.getReceivedContentMic());
        assertEquals("Unexpected Encoded Message Digest", EXPECTED_ENCODED_MESSAGE_DIGEST, messageDispositionNotificationEntity.getReceivedContentMic().getEncodedMessageDigest());
        assertEquals("Unexpected Digest Algorithm ID", EXPECTED_DIGEST_ALGORITHM_ID, messageDispositionNotificationEntity.getReceivedContentMic().getDigestAlgorithmId());
    }

}
